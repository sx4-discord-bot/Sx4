package com.sx4.bot.managers;

import club.minnced.discord.webhook.exception.HttpException;
import club.minnced.discord.webhook.send.WebhookMessage;
import com.mongodb.client.model.Updates;
import com.sx4.bot.core.Sx4;
import com.sx4.bot.entities.webhook.ReadonlyMessage;
import com.sx4.bot.entities.webhook.WebhookClient;
import com.sx4.bot.exceptions.mod.BotPermissionException;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.TextChannel;
import okhttp3.OkHttpClient;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class StarboardManager implements WebhookManager {

	public static final List<Document> DEFAULT_CONFIGURATION =  List.of(
		new Document("stars", 3).append("message", new Document("content", "⭐ **{stars}** {channel.mention}")),
		new Document("stars", 10).append("message", new Document("content", "🌟 **{stars}** {channel.mention}")),
		new Document("stars", 25).append("message", new Document("content", "🌠 **{stars}** {channel.mention}")),
		new Document("stars", 50).append("message", new Document("content", "✨ **{stars}** {channel.mention}")),
		new Document("stars", 100).append("message", new Document("content", "🎆 **{stars}** {channel.mention}"))
	);

	private final Map<Long, WebhookClient> webhooks;

	private final OkHttpClient client = new OkHttpClient();
	private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

	private final Sx4 bot;

	public StarboardManager(Sx4 bot) {
		this.webhooks = new HashMap<>();
		this.bot = bot;
	}

	public WebhookClient getWebhook(long id) {
		return this.webhooks.get(id);
	}

	public WebhookClient removeWebhook(long id) {
		return this.webhooks.remove(id);
	}

	public void putWebhook(long id, WebhookClient webhook) {
		this.webhooks.put(id, webhook);
	}

	private CompletableFuture<ReadonlyMessage> createWebhook(TextChannel channel, WebhookMessage message) {
		if (!channel.getGuild().getSelfMember().hasPermission(channel, Permission.MANAGE_WEBHOOKS)) {
			return CompletableFuture.failedFuture(new BotPermissionException(Permission.MANAGE_WEBHOOKS));
		}

		return channel.createWebhook("Sx4 - Starboard").submit().thenCompose(webhook -> {
			WebhookClient webhookClient = new WebhookClient(webhook.getIdLong(), webhook.getToken(), this.executor, this.client);

			this.webhooks.put(channel.getIdLong(), webhookClient);

			Bson update = Updates.combine(
				Updates.set("starboard.webhook.id", webhook.getIdLong()),
				Updates.set("starboard.webhook.token", webhook.getToken())
			);

			return this.bot.getMongo().updateGuildById(channel.getGuild().getIdLong(), update)
				.thenCompose(result -> webhookClient.send(message))
				.thenApply(webhookMessage -> new ReadonlyMessage(webhookMessage, webhook.getIdLong(), webhook.getToken()));
		}).exceptionallyCompose(exception -> {
			if (exception instanceof HttpException && ((HttpException) exception).getCode() == 404) {
				this.webhooks.remove(channel.getIdLong());

				return this.createWebhook(channel, message);
			}

			return CompletableFuture.failedFuture(exception);
		});
	}

	public CompletableFuture<ReadonlyMessage> sendStarboard(TextChannel channel, Document webhookData, WebhookMessage message) {
		WebhookClient webhook;
		if (this.webhooks.containsKey(channel.getIdLong())) {
			webhook = this.webhooks.get(channel.getIdLong());
		} else if (!webhookData.containsKey("id")) {
			return this.createWebhook(channel, message);
		} else {
			webhook = new WebhookClient(webhookData.getLong("id"), webhookData.getString("token"), this.executor, this.client);

			this.webhooks.put(channel.getIdLong(), webhook);
		}

		return webhook.send(message)
			.thenApply(webhookMessage -> new ReadonlyMessage(webhookMessage, webhook.getId(), webhook.getToken()))
			.exceptionallyCompose(exception -> {
				if (exception instanceof HttpException && ((HttpException) exception).getCode() == 404) {
					this.webhooks.remove(channel.getIdLong());

					return this.createWebhook(channel, message);
				}

				return CompletableFuture.failedFuture(exception);
			});
	}

	public CompletableFuture<ReadonlyMessage> editStarboard(long messageId, long channelId, Document webhookData, WebhookMessage message) {
		WebhookClient webhook;
		if (this.webhooks.containsKey(channelId)) {
			webhook = this.webhooks.get(channelId);
		} else if (!webhookData.containsKey("id")) {
			return CompletableFuture.completedFuture(null);
		} else {
			webhook = new WebhookClient(webhookData.getLong("id"), webhookData.getString("token"), this.executor, this.client);

			this.webhooks.put(channelId, webhook);
		}

		return webhook.edit(messageId, message).thenApply(webhookMessage -> new ReadonlyMessage(webhookMessage, webhook.getId(), webhook.getToken()));
	}

	public CompletableFuture<Void> deleteStarboard(long messageId, long channelId, Document webhookData) {
		WebhookClient webhook;
		if (this.webhooks.containsKey(channelId)) {
			webhook = this.webhooks.get(channelId);
		} else if (!webhookData.containsKey("id")) {
			return CompletableFuture.completedFuture(null);
		} else {
			webhook = new WebhookClient(webhookData.getLong("id"), webhookData.getString("token"), this.executor, this.client);

			this.webhooks.put(channelId, webhook);
		}

		return webhook.delete(messageId);
	}

}
