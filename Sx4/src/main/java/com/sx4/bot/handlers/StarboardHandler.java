package com.sx4.bot.handlers;

import club.minnced.discord.webhook.send.WebhookEmbed;
import club.minnced.discord.webhook.send.WebhookEmbedBuilder;
import club.minnced.discord.webhook.send.WebhookMessage;
import club.minnced.discord.webhook.send.WebhookMessageBuilder;
import com.mongodb.ErrorCategory;
import com.mongodb.MongoWriteException;
import com.mongodb.client.model.*;
import com.sx4.bot.core.Sx4;
import com.sx4.bot.database.mongo.MongoDatabase;
import com.sx4.bot.database.mongo.model.Operators;
import com.sx4.bot.formatter.IFormatter;
import com.sx4.bot.formatter.JsonFormatter;
import com.sx4.bot.managers.StarboardManager;
import com.sx4.bot.utility.ExceptionUtility;
import com.sx4.bot.utility.MessageUtility;
import com.sx4.bot.utility.StringUtility;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.Message.Attachment;
import net.dv8tion.jda.api.entities.MessageReaction.ReactionEmote;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.message.guild.react.GuildMessageReactionAddEvent;
import net.dv8tion.jda.api.events.message.guild.react.GuildMessageReactionRemoveEvent;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.internal.requests.RestActionImpl;
import net.dv8tion.jda.internal.requests.Route;
import net.dv8tion.jda.internal.utils.EncodingUtil;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public class StarboardHandler implements EventListener {

	private final Sx4 bot;

	public StarboardHandler(Sx4 bot) {
		this.bot = bot;
	}

	private WebhookMessage getStarboardMessage(Document guildData, Document starboard, Guild guild, Member member, ReactionEmote emote, boolean premium) {
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

		TextChannel channel = guild.getTextChannelById(channelId);
		if (channel == null) {
			return null;
		}

		String messageLink = "https://discord.com/channels/" + guild.getId() + "/" + channelId + "/" + starboard.getLong("originalMessageId");

		// temporary while embed support isn't a thing
		WebhookEmbedBuilder builder = new WebhookEmbedBuilder()
			.setAuthor(new WebhookEmbed.EmbedAuthor(author == null ? "Anonymous#0000" : author.getAsTag(), author == null ? null : author.getEffectiveAvatarUrl(), null))
			.setColor(-21453)
			.addField(new WebhookEmbed.EmbedField(false, "Message Link", "[Jump!](" + messageLink + ")"))
			.setImageUrl(starboard.getString("image"));

		String content = starboard.getString("content");
		if (content != null && !content.isBlank()) {
			builder.addField(new WebhookEmbed.EmbedField(false, "Message", StringUtility.limit(content, MessageEmbed.VALUE_MAX_LENGTH, "[...](" + messageLink + ")")));
		}

		Document webhookData = guildData.get("webhook", MongoDatabase.EMPTY_DOCUMENT);

		try {
			return this.format(messageData.get("message", Document.class), member, channel, emote, stars, nextStars, starboard.getObjectId("_id"))
				.setUsername(premium ? webhookData.get("name", "Sx4 - Starboard") : "Sx4 - Starboard")
				.setAvatarUrl(premium ? webhookData.get("avatar", this.bot.getShardManager().getShardById(0).getSelfUser().getEffectiveAvatarUrl()) : this.bot.getShardManager().getShardById(0).getSelfUser().getEffectiveAvatarUrl())
				.addEmbeds(builder.build())
				.build();
		} catch (IllegalArgumentException e) {
			// TODO: can't currently happen but when embed support is added handle this
			return null;
		}
	}

	private WebhookMessageBuilder format(Document message, Member member, TextChannel channel, ReactionEmote emote, int stars, int nextStars, ObjectId id) {
		IFormatter<Document> formatter = new JsonFormatter(message)
			.member(member)
			.user(member.getUser())
			.channel(channel)
			.emote(emote)
			.addVariable("stars", stars)
			.addVariable("stars.next", nextStars)
			.addVariable("stars.next.until", nextStars - stars)
			.addVariable("id", id.toHexString());

		return MessageUtility.fromJson(formatter.parse());
	}

	public void onGuildMessageReactionAdd(GuildMessageReactionAddEvent event) {
		if (event.getUser().isBot()) {
			return;
		}

		List<Bson> starboardPipeline = List.of(
			Aggregates.match(Filters.or(Filters.eq("originalMessageId", event.getMessageIdLong()), Filters.eq("messageId", event.getMessageIdLong()))),
			Aggregates.project(Projections.include("originalMessageId"))
		);

		List<Bson> pipeline = List.of(
			Aggregates.match(Filters.eq("_id", event.getGuild().getIdLong())),
			Aggregates.project(Projections.fields(Projections.include("starboard"), Projections.computed("premium", Operators.lt(Operators.nowEpochSecond(), Operators.ifNull("$premium.endAt", 0L))))),
			Aggregates.unionWith("starboards", starboardPipeline),
			Aggregates.group(null, Accumulators.max("messageId", "$originalMessageId"), Accumulators.max("starboard", "$starboard"), Accumulators.max("premium", "$premium"))
		);

		this.bot.getMongo().aggregateGuilds(pipeline).whenComplete((iterable, aggregateException) -> {
			if (ExceptionUtility.sendErrorMessage(aggregateException)) {
				return;
			}

			Document data = iterable.first();
			if (data == null) {
				return;
			}

			Document starboard = data.get("starboard", MongoDatabase.EMPTY_DOCUMENT);
			if (!starboard.get("enabled", false)) {
				return;
			}

			long channelId = starboard.get("channelId", 0L);

			TextChannel channel = channelId == 0L ? null : event.getGuild().getTextChannelById(channelId);
			if (channel == null) {
				return;
			}

			ReactionEmote emote = event.getReactionEmote();
			boolean emoji = emote.isEmoji();

			Document emoteData = starboard.get("emote", new Document("name", "⭐"));
			if ((emoji && !emote.getEmoji().equals(emoteData.getString("name"))) || (!emoji && (!emoteData.containsKey("id") || emoteData.getLong("id") != emote.getIdLong()))) {
				return;
			}

			Long originalMessageId = data.getLong("messageId");
			long messageId = originalMessageId == null ? event.getMessageIdLong() : originalMessageId;

			event.retrieveMessage().queue(message -> {
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
					WebhookMessage webhookMessage = this.getStarboardMessage(starboard, updatedData, event.getGuild(), event.getMember(), emote, data.getBoolean("premium"));
					if (webhookMessage == null) {
						return CompletableFuture.completedFuture(null);
					}

					if (updatedData.containsKey("messageId")) {
						this.bot.getStarboardManager().editStarboard(updatedData.getLong("messageId"), channel.getIdLong(), starboard.get("webhook", MongoDatabase.EMPTY_DOCUMENT), webhookMessage);
						return CompletableFuture.completedFuture(null); // return null so no update is made in the next stage
					} else {
						return this.bot.getStarboardManager().sendStarboard(channel, starboard.get("webhook", MongoDatabase.EMPTY_DOCUMENT), webhookMessage);
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
						Route.CompiledRoute route = Route.Messages.ADD_REACTION.compile(Long.toString(createdMessage.getChannelId()), Long.toString(createdMessage.getId()), EncodingUtil.encodeReaction(emote.getAsReactionCode()), "@me");
						new RestActionImpl<>(event.getJDA(), route).queue(null, ErrorResponseException.ignore(ErrorResponse.UNKNOWN_EMOJI, ErrorResponse.MISSING_PERMISSIONS, ErrorResponse.MISSING_ACCESS));

						this.bot.getMongo().updateStarboard(Filters.eq("originalMessageId", messageId), Updates.set("messageId", createdMessage.getId())).whenComplete(MongoDatabase.exceptionally(event.getJDA().getShardManager()));
					}
				});
			});
		});
	}

	public void onGuildMessageReactionRemove(GuildMessageReactionRemoveEvent event) {
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

		this.bot.getMongo().aggregateGuilds(pipeline).whenComplete((iterable, aggregateException) -> {
			if (ExceptionUtility.sendErrorMessage(aggregateException)) {
				return;
			}

			Document data = iterable.first();
			if (data == null) {
				return;
			}

			Document starboard = data.get("starboard", MongoDatabase.EMPTY_DOCUMENT);
			if (!starboard.get("enabled", false)) {
				return;
			}

			long channelId = starboard.get("channelId", 0L);

			TextChannel channel = channelId == 0L ? null : event.getGuild().getTextChannelById(channelId);
			if (channel == null) {
				return;
			}

			ReactionEmote emote = event.getReactionEmote();
			boolean emoji = emote.isEmoji();

			Document emoteData = starboard.get("emote", new Document("name", "⭐"));
			if ((emoji && !emote.getEmoji().equals(emoteData.getString("name"))) || (!emoji && (!emoteData.containsKey("id") || emoteData.getLong("id") != emote.getIdLong()))) {
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
					Operators.set("count", Operators.subtract("$count", 1)),
					Operators.set("messageId", Operators.cond(Operators.isEmpty(Operators.filter(config, Operators.gte(Operators.subtract("$count", 1), "$$this.stars"))), Operators.REMOVE, "$messageId"))
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

				WebhookMessage webhookMessage = this.getStarboardMessage(starboard, updatedData, event.getGuild(), event.getMember(), emote, data.getBoolean("premium"));
				if (webhookMessage == null) {
					this.bot.getStarboardManager().deleteStarboard(data.getLong("messageId"), channel.getIdLong(), starboard.get("webhook", MongoDatabase.EMPTY_DOCUMENT));
				} else {
					this.bot.getStarboardManager().editStarboard(data.getLong("messageId"), channel.getIdLong(), starboard.get("webhook", MongoDatabase.EMPTY_DOCUMENT), webhookMessage);
				}
			});
		});
	}


	@Override
	public void onEvent(GenericEvent event) {
		if (event instanceof GuildMessageReactionAddEvent) {
			this.onGuildMessageReactionAdd((GuildMessageReactionAddEvent) event);
		} else if (event instanceof GuildMessageReactionRemoveEvent) {
			this.onGuildMessageReactionRemove((GuildMessageReactionRemoveEvent) event);
		}
	}

}
