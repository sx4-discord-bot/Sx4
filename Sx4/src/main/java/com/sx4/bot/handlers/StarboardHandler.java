package com.sx4.bot.handlers;

import com.mongodb.ErrorCategory;
import com.mongodb.MongoWriteException;
import com.mongodb.client.model.*;
import com.sx4.bot.core.Sx4;
import com.sx4.bot.database.mongo.MongoDatabase;
import com.sx4.bot.database.mongo.model.Operators;
import com.sx4.bot.entities.webhook.WebhookChannel;
import com.sx4.bot.formatter.output.Formatter;
import com.sx4.bot.formatter.output.JsonFormatter;
import com.sx4.bot.managers.StarboardManager;
import com.sx4.bot.utility.ExceptionUtility;
import com.sx4.bot.utility.MessageUtility;
import com.sx4.bot.utility.StringUtility;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message.Attachment;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.channel.unions.GuildMessageChannelUnion;
import net.dv8tion.jda.api.entities.emoji.EmojiUnion;
import net.dv8tion.jda.api.entities.emoji.UnicodeEmoji;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionRemoveEvent;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.requests.Route;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.internal.requests.RestActionImpl;
import net.dv8tion.jda.internal.utils.EncodingUtil;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public class StarboardHandler implements EventListener {

	private final Sx4 bot;

	public StarboardHandler(Sx4 bot) {
		this.bot = bot;
	}

	private MessageCreateData getStarboardMessage(Document guildData, Document starboard, Guild guild, Member member, EmojiUnion emoji) {
		List<Document> messages = guildData.getList("messages", Document.class, StarboardManager.DEFAULT_CONFIGURATION);

		int stars = starboard.getInteger("count");

		Document messageData = messages.stream()
			.filter(d -> starboard.getInteger("count") >= d.getInteger("stars"))
			.max(Comparator.comparingInt(d -> d.getInteger("stars")))
			.orElse(null);

		if (messageData == null) {
			return null;
		}

		int nextStars = messages.stream()
			.filter(d -> starboard.getInteger("count") < d.getInteger("stars"))
			.mapToInt(d -> d.getInteger("stars"))
			.min()
			.orElse(0);

		User author = this.bot.getShardManager().getUserById(starboard.getLong("authorId"));

		long channelId = starboard.getLong("channelId");

		GuildMessageChannel channel = guild.getChannelById(GuildMessageChannel.class, channelId);
		if (channel == null) {
			return null;
		}

		String messageLink = "https://discord.com/channels/" + guild.getId() + "/" + channelId + "/" + starboard.getLong("originalMessageId");

		// temporary while embed support isn't a thing
		EmbedBuilder builder = new EmbedBuilder()
			.setAuthor(author == null ? "Anonymous#0000" : author.getAsTag(), null, author == null ? null : author.getEffectiveAvatarUrl())
			.setColor(-21453)
			.addField("Message Link", "[Jump!](" + messageLink + ")", false)
			.setImage(starboard.getString("image"));

		String content = starboard.getString("content");
		if (content != null && !content.isBlank()) {
			builder.addField("Message", StringUtility.limit(content, MessageEmbed.VALUE_MAX_LENGTH, "[...](" + messageLink + ")"), false);
		}

		try {
			return this.format(messageData.get("message", Document.class), member, channel, emoji, stars, nextStars, starboard.getObjectId("_id"))
				.addEmbeds(builder.build())
				.build();
		} catch (IllegalArgumentException e) {
			// TODO: can't currently happen but when embed support is added handle this
			return null;
		}
	}

	private MessageCreateBuilder format(Document message, Member member, MessageChannel channel, EmojiUnion emoji, int stars, int nextStars, ObjectId id) {
		Formatter<Document> formatter = new JsonFormatter(message)
			.member(member)
			.user(member.getUser())
			.channel(channel)
			.emoji(emoji)
			.addVariable("stars", stars)
			.addVariable("stars.next", nextStars)
			.addVariable("stars.next.until", nextStars - stars)
			.addVariable("id", id.toHexString());

		return MessageUtility.fromCreateJson(formatter.parse(), true);
	}

	public void onMessageReactionAdd(MessageReactionAddEvent event) {
		if (!event.isFromGuild()) {
			return;
		}

		if (event.getUser().isBot()) {
			return;
		}

		List<Bson> starboardPipeline = List.of(
			Aggregates.match(Filters.or(Filters.eq("originalMessageId", event.getMessageIdLong()), Filters.eq("messageId", event.getMessageIdLong()))),
			Aggregates.project(Projections.include("originalMessageId", "channelId"))
		);

		List<Bson> pipeline = List.of(
			Aggregates.match(Filters.eq("_id", event.getGuild().getIdLong())),
			Aggregates.project(Projections.fields(Projections.include("starboard"), Projections.computed("premium", Operators.lt(Operators.nowEpochSecond(), Operators.ifNull("$premium.endAt", 0L))))),
			Aggregates.unionWith("starboards", starboardPipeline),
			Aggregates.group(null, Accumulators.max("messageId", "$originalMessageId"), Accumulators.max("channelId", "$channelId"), Accumulators.max("starboard", "$starboard"), Accumulators.max("premium", "$premium"))
		);

		this.bot.getMongo().aggregateGuilds(pipeline).whenComplete((documents, aggregateException) -> {
			if (ExceptionUtility.sendErrorMessage(aggregateException)) {
				return;
			}

			if (documents.isEmpty()) {
				return;
			}

			Document data = documents.get(0);
			Document starboard = data.get("starboard", MongoDatabase.EMPTY_DOCUMENT);
			if (!starboard.get("enabled", false)) {
				return;
			}

			long channelId = starboard.get("channelId", 0L), messageChannelId = data.get("channelId", 0L);

			GuildMessageChannel messageChannel = messageChannelId == 0L ? (GuildMessageChannel) event.getChannel() : event.getGuild().getChannelById(GuildMessageChannel.class, messageChannelId);
			GuildMessageChannelUnion channel = channelId == 0L ? null : event.getGuild().getChannelById(GuildMessageChannelUnion.class, channelId);

			if (channel == null || messageChannel == null) {
				return;
			}

			EmojiUnion emoji = event.getEmoji();
			boolean unicode = emoji instanceof UnicodeEmoji;

			Document emoteData = starboard.get("emote", new Document("name", "⭐"));
			if ((unicode && !emoji.getName().equals(emoteData.getString("name"))) || (!unicode && (!emoteData.containsKey("id") || emoteData.getLong("id") != emoji.asCustom().getIdLong()))) {
				return;
			}

			Long originalMessageId = data.getLong("messageId");
			long messageId = originalMessageId == null ? event.getMessageIdLong() : originalMessageId;

			messageChannel.retrieveMessageById(messageId).queue(message -> {
				String image = message.getAttachments().stream()
					.filter(Attachment::isImage)
					.map(Attachment::getUrl)
					.findFirst()
					.orElse(null);

				Document star = new Document("userId", event.getUser().getIdLong())
					.append("messageId", messageId)
					.append("guildId", event.getGuild().getIdLong());

				this.bot.getMongo().insertStar(star).thenCompose(result -> {
					Bson update = Updates.combine(
						Updates.inc("count", 1),
						Updates.setOnInsert("originalMessageId", messageId),
						Updates.setOnInsert("guildId", event.getGuild().getIdLong()),
						Updates.setOnInsert("channelId", event.getChannel().getIdLong()),
						Updates.set("content", message.getContentRaw()),
						Updates.set("authorId", message.getAuthor().getIdLong())
					);

					if (image != null) {
						update = Updates.combine(update, Updates.set("image", image));
					}

					FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER).upsert(true);
					return this.bot.getMongo().findAndUpdateStarboard(Filters.eq("originalMessageId", messageId), update, options);
				}).thenCompose(updatedData -> {
					MessageCreateData webhookMessage = this.getStarboardMessage(starboard, updatedData, event.getGuild(), event.getMember(), emoji);
					if (webhookMessage == null) {
						return CompletableFuture.completedFuture(null);
					}

					if (updatedData.containsKey("messageId")) {
						this.bot.getStarboardManager().editStarboard(updatedData.getLong("messageId"), new WebhookChannel(channel), starboard.get("webhook", MongoDatabase.EMPTY_DOCUMENT), webhookMessage);
						return CompletableFuture.completedFuture(null); // return null so no update is made in the next stage
					} else {
						return this.bot.getStarboardManager().sendStarboard(new WebhookChannel(channel), starboard.get("webhook", MongoDatabase.EMPTY_DOCUMENT), webhookMessage, data.getBoolean("premium"));
					}
				}).whenComplete((createdMessage, exception) -> {
					if (exception instanceof CompletionException) {
						Throwable cause = exception.getCause();
						if (cause instanceof MongoWriteException && ((MongoWriteException) cause).getError().getCategory() == ErrorCategory.DUPLICATE_KEY) {
							return; // duplicate star just ignore
						}
					}

					if (ExceptionUtility.sendErrorMessage(exception)) {
						return;
					}

					if (createdMessage != null) {
						Route.CompiledRoute route = Route.Messages.ADD_REACTION.compile(createdMessage.getChannelId(), createdMessage.getId(), EncodingUtil.encodeReaction(emoji.getAsReactionCode()), "@me");
						new RestActionImpl<>(event.getJDA(), route).queue(null, ErrorResponseException.ignore(ErrorResponse.UNKNOWN_EMOJI, ErrorResponse.MISSING_PERMISSIONS, ErrorResponse.MISSING_ACCESS));

						this.bot.getMongo().updateStarboard(Filters.eq("originalMessageId", messageId), Updates.set("messageId", createdMessage.getIdLong())).whenComplete(MongoDatabase.exceptionally());
					}
				});
			});
		});
	}

	public void onMessageReactionRemove(MessageReactionRemoveEvent event) {
		if (!event.isFromGuild()) {
			return;
		}

		User user = event.getUser();
		if (user == null || user.isBot()) {
			return;
		}

		List<Bson> starboardPipeline = List.of(
			Aggregates.match(Filters.or(Filters.eq("originalMessageId", event.getMessageIdLong()), Filters.eq("messageId", event.getMessageIdLong()))),
			Aggregates.project(Projections.include("originalMessageId", "messageId", "count"))
		);

		List<Bson> pipeline = List.of(
			Aggregates.match(Filters.eq("_id", event.getGuild().getIdLong())),
			Aggregates.project(Projections.fields(Projections.include("starboard"), Projections.computed("premium", Operators.lt(Operators.nowEpochSecond(), Operators.ifNull("$premium.endAt", 0L))))),
			Aggregates.unionWith("starboards", starboardPipeline),
			Aggregates.group(null, Accumulators.max("count", "$count"), Accumulators.max("messageId", "$messageId"), Accumulators.max("originalMessageId", "$originalMessageId"), Accumulators.max("starboard", "$starboard"), Accumulators.max("premium", "$premium"))
		);

		this.bot.getMongo().aggregateGuilds(pipeline).whenComplete((documents, aggregateException) -> {
			if (ExceptionUtility.sendErrorMessage(aggregateException)) {
				return;
			}

			if (documents.isEmpty()) {
				return;
			}

			Document data = documents.get(0);
			Document starboard = data.get("starboard", MongoDatabase.EMPTY_DOCUMENT);
			if (!starboard.get("enabled", false)) {
				return;
			}

			long channelId = starboard.get("channelId", 0L);

			GuildMessageChannelUnion channel = channelId == 0L ? null : event.getGuild().getChannelById(GuildMessageChannelUnion.class, channelId);
			if (channel == null) {
				return;
			}

			EmojiUnion emoji = event.getEmoji();
			boolean unicode = emoji instanceof UnicodeEmoji;

			Document emoteData = starboard.get("emote", new Document("name", "⭐"));
			if ((unicode && !emoji.getName().equals(emoteData.getString("name"))) || (!unicode && (!emoteData.containsKey("id") || emoteData.getLong("id") != emoji.asCustom().getIdLong()))) {
				return;
			}

			Long originalMessageId = data.getLong("originalMessageId");
			if (originalMessageId == null) {
				return;
			}

			List<Document> config = starboard.getList("messages", Document.class, StarboardManager.DEFAULT_CONFIGURATION);

			this.bot.getMongo().deleteStarById(event.getUserIdLong(), originalMessageId).thenCompose(result -> {
				if (result.getDeletedCount() == 0) {
					return CompletableFuture.completedFuture(null);
				}

				List<Bson> update = List.of(
					Operators.set("messageId", Operators.cond(Operators.isEmpty(Operators.filter(config, Operators.gte(Operators.subtract("$count", 1), "$$this.stars"))), Operators.REMOVE, "$messageId")),
					Operators.set("count", Operators.subtract("$count", 1))
				);

				FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER);

				return this.bot.getMongo().findAndUpdateStarboard(Filters.eq("originalMessageId", originalMessageId), update, options);
			}).whenComplete((updatedData, exception) -> {
				if (ExceptionUtility.sendErrorMessage(exception) || updatedData == null) {
					return;
				}

				if (!data.containsKey("messageId")) {
					return;
				}

				MessageCreateData webhookMessage = this.getStarboardMessage(starboard, updatedData, event.getGuild(), event.getMember(), emoji);
				if (webhookMessage == null) {
					this.bot.getStarboardManager().deleteStarboard(data.getLong("messageId"), new WebhookChannel(channel), starboard.get("webhook", MongoDatabase.EMPTY_DOCUMENT));
				} else {
					this.bot.getStarboardManager().editStarboard(data.getLong("messageId"), new WebhookChannel(channel), starboard.get("webhook", MongoDatabase.EMPTY_DOCUMENT), webhookMessage);
				}
			});
		});
	}


	@Override
	public void onEvent(@NotNull GenericEvent event) {
		if (event instanceof MessageReactionAddEvent) {
			this.onMessageReactionAdd((MessageReactionAddEvent) event);
		} else if (event instanceof MessageReactionRemoveEvent) {
			this.onMessageReactionRemove((MessageReactionRemoveEvent) event);
		}
	}

}
