package com.sx4.bot.handlers;

import club.minnced.discord.webhook.send.WebhookEmbed;
import club.minnced.discord.webhook.send.WebhookEmbedBuilder;
import club.minnced.discord.webhook.send.WebhookMessage;
import club.minnced.discord.webhook.send.WebhookMessageBuilder;
import com.mongodb.ErrorCategory;
import com.mongodb.MongoWriteException;
import com.mongodb.client.model.*;
import com.sx4.bot.core.Sx4;
import com.sx4.bot.database.Database;
import com.sx4.bot.formatter.Formatter;
import com.sx4.bot.formatter.JsonFormatter;
import com.sx4.bot.managers.StarboardManager;
import com.sx4.bot.utility.ExceptionUtility;
import com.sx4.bot.utility.MessageUtility;
import com.sx4.bot.utility.NumberUtility;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.Message.Attachment;
import net.dv8tion.jda.api.entities.MessageReaction.ReactionEmote;
import net.dv8tion.jda.api.events.message.guild.react.GuildMessageReactionAddEvent;
import net.dv8tion.jda.api.events.message.guild.react.GuildMessageReactionRemoveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.RestAction;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

public class StarboardHandler extends ListenerAdapter {

	private final Database database = Database.get();
	private final StarboardManager manager = StarboardManager.get();

	private WebhookMessage getStarboardMessage(Document guildData, Document starboard, Guild guild, Member member, ReactionEmote emote) {
		List<Document> messages = guildData.getList("messages", Document.class, StarboardManager.DEFAULT_CONFIGURATION);

		Document messageData = messages.stream()
			.dropWhile(d -> starboard.getInteger("count") < d.getInteger("stars"))
			.max(Comparator.comparingInt(d -> d.getInteger("stars")))
			.orElse(null);

		if (messageData == null) {
			return null;
		}

		int stars = messageData.getInteger("stars");

		int nextStars = messages.stream()
			.dropWhile(d -> starboard.getInteger("count") > d.getInteger("stars"))
			.mapToInt(d -> d.getInteger("stars"))
			.min()
			.orElse(0);

		User author = Sx4.get().getShardManager().getUserById(starboard.getLong("authorId"));

		long channelId = starboard.getLong("channelId");

		TextChannel channel = guild.getTextChannelById(channelId);
		if (channel == null) {
			return null;
		}

		// temporary while embed support isn't a thing
		WebhookEmbedBuilder builder = new WebhookEmbedBuilder()
			.setAuthor(new WebhookEmbed.EmbedAuthor(author == null ? "Anonymous#0000" : author.getAsTag(), author == null ? null : author.getEffectiveAvatarUrl(), null))
			.setColor(-21453)
			.addField(new WebhookEmbed.EmbedField(false, "Message Link", String.format("[Jump!](https://discord.com/channels/%s/%d/%d)", guild.getId(), channelId, starboard.getLong("originalMessageId"))))
			.setImageUrl(starboard.getString("image"));

		String content = starboard.getString("content");
		if (content != null) {
			builder.addField(new WebhookEmbed.EmbedField(false, "Message", content));
		}

		Document webhookData = guildData.get("webhook", Database.EMPTY_DOCUMENT);

		try {
			return this.format(messageData.get("message", Document.class), member, channel, emote, stars, nextStars, starboard.getObjectId("_id"))
				.setUsername(webhookData.get("name", "Sx4 - Starboard"))
				.setAvatarUrl(webhookData.get("avatar", Sx4.get().getShardManager().getShardById(0).getSelfUser().getEffectiveAvatarUrl()))
				.addEmbeds(builder.build())
				.build();
		} catch (IllegalArgumentException e) {
			// TODO: can't currently happen but when embed support is added handle this
			return null;
		}
	}

	private WebhookMessageBuilder format(Document message, Member member, TextChannel channel, ReactionEmote emote, int stars, int nextStars, ObjectId id) {
		Formatter<Document> formatter = new JsonFormatter(message)
			.member(member)
			.channel(channel)
			.emote(emote)
			.append("stars", stars)
			.append("stars.suffix", NumberUtility.getSuffixed(stars))
			.append("stars.next", nextStars)
			.append("stars.next.suffix", NumberUtility.getSuffixed(nextStars))
			.append("stars.next.until", nextStars - stars)
			.append("stars.next.until.suffix", NumberUtility.getSuffixed(nextStars - stars))
			.append("id", id.toHexString());

		return MessageUtility.fromJson(formatter.parse());
	}

	private void getMessageData(RestAction<Message> action, Consumer<Message> consumer) {
		if (action == null) {
			consumer.accept(null);
		} else {
			action.queue(consumer);
		}
	}

	public void onGuildMessageReactionAdd(GuildMessageReactionAddEvent event) {
		if (event.getUser().isBot()) {
			return;
		}

		Document data = this.database.getGuildById(event.getGuild().getIdLong(), Projections.include("starboard")).get("starboard", Database.EMPTY_DOCUMENT);
		if (!data.get("enabled", false)) {
			return;
		}

		long channelId = data.get("channelId", 0L);

		TextChannel channel = channelId == 0L ? null : event.getGuild().getTextChannelById(channelId);
		if (channel == null) {
			return;
		}

		ReactionEmote emote = event.getReactionEmote();
		boolean emoji = emote.isEmoji();

		Document emoteData = data.get("emote", new Document("name", "⭐"));
		if ((emoji && !emote.getEmoji().equals(emoteData.getString("name"))) || (!emoji && (!emoteData.containsKey("id") || emoteData.getLong("id") != emote.getIdLong()))) {
			return;
		}

		Bson filter = Filters.or(Filters.eq("originalMessageId", event.getMessageIdLong()), Filters.eq("messageId", event.getMessageIdLong()));

		Document starboard = this.database.getStarboard(filter, Projections.include("originalMessageId"));

		long messageId = starboard == null ? event.getMessageIdLong() : starboard.getLong("originalMessageId");

		this.getMessageData(starboard == null ? event.retrieveMessage() : null, message -> {
			String image = message == null ? null : message.getAttachments().stream()
				.filter(Attachment::isImage)
				.map(Attachment::getUrl)
				.findFirst()
				.orElse(null);

			Document star = new Document("userId", event.getUser().getIdLong())
				.append("messageId", messageId)
				.append("guildId", event.getGuild().getIdLong());

			this.database.insertStar(star).thenCompose(result -> {
				Bson update = Updates.combine(
					Updates.inc("count", 1),
					Updates.setOnInsert("originalMessageId", messageId),
					Updates.setOnInsert("guildId", event.getGuild().getIdLong()),
					Updates.setOnInsert("channelId", event.getChannel().getIdLong())
				);

				if (message != null) {
					update = Updates.combine(
						update,
						Updates.set("content", message.getContentRaw()),
						Updates.set("authorId", message.getAuthor().getIdLong())
					);

					if (image != null) {
						update = Updates.combine(update, Updates.set("image", image));
					}
				}

				FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER).upsert(true);
				return this.database.findAndUpdateStarboard(Filters.eq("originalMessageId", messageId), update, options);
			}).thenCompose(updatedData -> {
				WebhookMessage webhookMessage = this.getStarboardMessage(data, updatedData, event.getGuild(), event.getMember(), emote);
				if (webhookMessage == null) {
					return CompletableFuture.completedFuture(null);
				}

				if (updatedData.containsKey("messageId")) {
					this.manager.editStarboard(updatedData.getLong("messageId"), channel.getIdLong(), data.get("webhook", Database.EMPTY_DOCUMENT), webhookMessage);
					return CompletableFuture.completedFuture(null); // return null so no update is made in the next stage
				} else {
					return this.manager.sendStarboard(channel, data.get("webhook", Database.EMPTY_DOCUMENT), webhookMessage);
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
					this.database.updateStarboard(Filters.eq("originalMessageId", messageId), Updates.set("messageId", createdMessage.getId())).whenComplete(Database.exceptionally());
				}
			});
		});
	}

	public void onGuildMessageReactionRemove(GuildMessageReactionRemoveEvent event) {
		User user = event.getUser();
		if (user == null || user.isBot()) {
			return;
		}

		Document data = this.database.getGuildById(event.getGuild().getIdLong(), Projections.include("starboard")).get("starboard", Database.EMPTY_DOCUMENT);
		if (!data.get("enabled", false)) {
			return;
		}

		long channelId = data.get("channelId", 0L);

		TextChannel channel = channelId == 0L ? null : event.getGuild().getTextChannelById(channelId);
		if (channel == null) {
			return;
		}

		ReactionEmote emote = event.getReactionEmote();
		boolean emoji = emote.isEmoji();

		Document emoteData = data.get("emote", new Document("name", "⭐"));
		if ((emoji && !emote.getEmoji().equals(emoteData.getString("name"))) || (!emoji && (!emoteData.containsKey("id") || emoteData.getLong("id") != emote.getIdLong()))) {
			return;
		}

		Bson filter = Filters.or(Filters.eq("originalMessageId", event.getMessageIdLong()), Filters.eq("messageId", event.getMessageIdLong()));

		Document starboard = this.database.getStarboard(filter, Projections.include("originalMessageId", "count", "messageId"));
		if (starboard == null) {
			return;
		}

		long messageId = starboard.get("originalMessageId", 0L);
		if (messageId == 0L) {
			return;
		}

		Document messageData = data.getList("messages", Document.class, StarboardManager.DEFAULT_CONFIGURATION).stream()
			.dropWhile(d -> starboard.getInteger("count") - 1 < d.getInteger("stars"))
			.max(Comparator.comparingInt(d -> d.getInteger("stars")))
			.orElse(null);

		this.database.deleteStarById(event.getUserIdLong(), messageId).thenCompose(result -> {
			if (result.getDeletedCount() == 0) {
				return CompletableFuture.completedFuture(null);
			}

			FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER);
			Bson update = Updates.inc("count", -1);
			if (messageData == null) {
				update = Updates.combine(update, Updates.unset("messageId"));
			}

			return this.database.findAndUpdateStarboard(Filters.eq("originalMessageId", messageId), update, options);
		}).whenComplete((updatedData, exception) -> {
			if (ExceptionUtility.sendErrorMessage(exception) || updatedData == null) {
				return;
			}

			WebhookMessage webhookMessage = this.getStarboardMessage(data, updatedData, event.getGuild(), event.getMember(), emote);
			if (webhookMessage == null) {
				this.manager.deleteStarboard(starboard.getLong("messageId"), channel.getIdLong(), data.get("webhook", Database.EMPTY_DOCUMENT));
			} else {
				this.manager.editStarboard(starboard.getLong("messageId"), channel.getIdLong(), data.get("webhook", Database.EMPTY_DOCUMENT), webhookMessage);
			}
		});
	}

}
