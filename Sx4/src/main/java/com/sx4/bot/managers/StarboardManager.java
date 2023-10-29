package com.sx4.bot.managers;

import com.mongodb.client.model.Updates;
import com.sx4.bot.core.Sx4;
import com.sx4.bot.database.mongo.MongoDatabase;
import com.sx4.bot.entities.webhook.SentWebhookMessage;
import com.sx4.bot.entities.webhook.WebhookChannel;
import com.sx4.bot.exceptions.mod.BotPermissionException;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.WebhookClient;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public class StarboardManager implements WebhookManager {

	public static final List<Document> DEFAULT_CONFIGURATION =  List.of(
		new Document("stars", 3).append("message", new Document("content", "⭐ **{stars}** {channel.mention}")),
		new Document("stars", 10).append("message", new Document("content", "🌟 **{stars}** {channel.mention}")),
		new Document("stars", 25).append("message", new Document("content", "🌠 **{stars}** {channel.mention}")),
		new Document("stars", 50).append("message", new Document("content", "✨ **{stars}** {channel.mention}")),
		new Document("stars", 100).append("message", new Document("content", "🎆 **{stars}** {channel.mention}"))
	);

	private final Map<Long, WebhookClient<Message>> webhooks;

	private final Sx4 bot;

	public StarboardManager(Sx4 bot) {
		this.webhooks = new HashMap<>();
		this.bot = bot;
	}

	public WebhookClient<Message> getWebhook(long id) {
		return this.webhooks.get(id);
	}

	public WebhookClient<Message> removeWebhook(long id) {
		return this.webhooks.remove(id);
	}

	public void putWebhook(long id, WebhookClient<Message> webhook) {
		this.webhooks.put(id, webhook);
	}

	private void disableStarboard(Guild guild) {
		Bson update = Updates.combine(
			Updates.unset("starboard.webhook.id"),
			Updates.unset("starboard.webhook.token"),
			Updates.unset("starboard.enabled")
		);

		this.bot.getMongo().updateGuildById(guild.getIdLong(), update).whenComplete(MongoDatabase.exceptionally());
	}

	private CompletableFuture<SentWebhookMessage> createWebhook(WebhookChannel channel, Document webhookData, MessageCreateData message, boolean premium) {
		if (!channel.getGuild().getSelfMember().hasPermission(channel, Permission.MANAGE_WEBHOOKS)) {
			this.disableStarboard(channel.getGuild());
			return CompletableFuture.failedFuture(new BotPermissionException(Permission.MANAGE_WEBHOOKS));
		}

		return channel.createWebhook("Sx4 - Starboard").submit().thenCompose(webhook -> {
			this.webhooks.put(channel.getIdLong(), webhook);

			Bson update = Updates.combine(
				Updates.set("starboard.webhook.id", webhook.getIdLong()),
				Updates.set("starboard.webhook.token", webhook.getToken())
			);

			WebhookMessageCreateAction<Message> action = webhook.sendMessage(message)
				.setUsername(premium ? webhookData.get("name", "Sx4 - Starboard") : "Sx4 - Starboard")
				.setAvatarUrl(premium ? webhookData.get("avatar", channel.getJDA().getSelfUser().getEffectiveAvatarUrl()) : channel.getJDA().getSelfUser().getEffectiveAvatarUrl());

			return this.bot.getMongo().updateGuildById(channel.getGuild().getIdLong(), update)
				.thenCompose(result -> channel.sendWebhookMessage(action))
				.thenApply(webhookMessage -> new SentWebhookMessage(webhookMessage, webhook.getIdLong(), webhook.getToken()));
		}).exceptionallyCompose(exception -> {
			Throwable cause = exception instanceof CompletionException ? exception.getCause() : exception;
			if (cause instanceof ErrorResponseException && ((ErrorResponseException) cause).getErrorCode() == 404) {
				this.webhooks.remove(channel.getIdLong());

				return this.createWebhook(channel, webhookData, message, premium);
			}

			return CompletableFuture.failedFuture(exception);
		});
	}

	public CompletableFuture<SentWebhookMessage> sendStarboard(WebhookChannel channel, Document webhookData, MessageCreateData message, boolean premium) {
		WebhookClient<Message> webhook;
		if (this.webhooks.containsKey(channel.getIdLong())) {
			webhook = this.webhooks.get(channel.getIdLong());
		} else if (!webhookData.containsKey("id")) {
			return this.createWebhook(channel, webhookData, message, premium);
		} else {
			webhook = WebhookClient.createClient(channel.getJDA(), Long.toString(webhookData.getLong("id")), webhookData.getString("token"));

			this.webhooks.put(channel.getIdLong(), webhook);
		}

		WebhookMessageCreateAction<Message> action = webhook.sendMessage(message)
			.setUsername(premium ? webhookData.get("name", "Sx4 - Starboard") : "Sx4 - Starboard")
			.setAvatarUrl(premium ? webhookData.get("avatar", channel.getJDA().getSelfUser().getEffectiveAvatarUrl()) : channel.getJDA().getSelfUser().getEffectiveAvatarUrl());

		return channel.sendWebhookMessage(action)
			.thenApply(webhookMessage -> new SentWebhookMessage(webhookMessage, webhook.getIdLong(), webhook.getToken()))
			.exceptionallyCompose(exception -> {
				Throwable cause = exception instanceof CompletionException ? exception.getCause() : exception;
				if (cause instanceof ErrorResponseException && ((ErrorResponseException) cause).getErrorCode() == 404) {
					this.webhooks.remove(channel.getIdLong());

					return this.createWebhook(channel, webhookData, message, premium);
				}

				return CompletableFuture.failedFuture(exception);
			});
	}

	public CompletableFuture<SentWebhookMessage> editStarboard(long messageId, WebhookChannel channel, Document webhookData, MessageCreateData message) {
		long channelId = channel.getIdLong();

		WebhookClient<Message> webhook;
		if (this.webhooks.containsKey(channelId)) {
			webhook = this.webhooks.get(channelId);
		} else if (!webhookData.containsKey("id")) {
			return CompletableFuture.completedFuture(null);
		} else {
			webhook = WebhookClient.createClient(channel.getJDA(), Long.toString(webhookData.getLong("id")), webhookData.getString("token"));

			this.webhooks.put(channelId, webhook);
		}

		return channel.editWebhookMessage(webhook.editMessageById(messageId, MessageEditData.fromCreateData(message))).thenApply(webhookMessage -> new SentWebhookMessage(webhookMessage, webhook.getIdLong(), webhook.getToken()));
	}

	public CompletableFuture<Void> deleteStarboard(long messageId, WebhookChannel channel, Document webhookData) {
		long channelId = channel.getIdLong();

		WebhookClient<Message> webhook;
		if (this.webhooks.containsKey(channelId)) {
			webhook = this.webhooks.get(channelId);
		} else if (!webhookData.containsKey("id")) {
			return CompletableFuture.completedFuture(null);
		} else {
			webhook = WebhookClient.createClient(channel.getJDA(), Long.toString(webhookData.getLong("id")), webhookData.getString("token"));

			this.webhooks.put(channelId, webhook);
		}

		return channel.deleteWebhookMessage(webhook.deleteMessageById(messageId));
	}

}
