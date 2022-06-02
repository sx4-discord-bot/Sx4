package com.sx4.bot.managers;

import club.minnced.discord.webhook.exception.HttpException;
import club.minnced.discord.webhook.send.WebhookMessage;
import com.mongodb.client.model.Updates;
import com.sx4.bot.core.Sx4;
import com.sx4.bot.database.mongo.MongoDatabase;
import com.sx4.bot.entities.webhook.ReadonlyMessage;
import com.sx4.bot.entities.webhook.WebhookClient;
import com.sx4.bot.exceptions.mod.BotPermissionException;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.BaseGuildMessageChannel;
import net.dv8tion.jda.api.entities.Guild;
import okhttp3.OkHttpClient;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class LeaverManager implements WebhookManager {

	public static final Document DEFAULT_MESSAGE = new Document("content", "**{user.name}** has just left **{server.name}**. Bye **{user.name}**!");

	private final Map<Long, WebhookClient> webhooks;

	private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
	private final OkHttpClient client = new OkHttpClient();

	private final Sx4 bot;

	public LeaverManager(Sx4 bot) {
		this.webhooks = new HashMap<>();
		this.bot = bot;
	}

	public WebhookClient getWebhook(long channelId) {
		return this.webhooks.get(channelId);
	}

	public WebhookClient removeWebhook(long channelId) {
		return this.webhooks.remove(channelId);
	}

	public void putWebhook(long id, WebhookClient webhook) {
		this.webhooks.put(id, webhook);
	}

	private void disableLeaver(Guild guild) {
		Bson update = Updates.combine(
			Updates.unset("leaver.webhook.id"),
			Updates.unset("leaver.webhook.token"),
			Updates.unset("leaver.enabled")
		);

		this.bot.getMongo().updateGuildById(guild.getIdLong(), update).whenComplete(MongoDatabase.exceptionally());
	}

	private CompletableFuture<ReadonlyMessage> createWebhook(BaseGuildMessageChannel channel, WebhookMessage message) {
		if (!channel.getGuild().getSelfMember().hasPermission(channel, Permission.MANAGE_WEBHOOKS)) {
			this.disableLeaver(channel.getGuild());
			return CompletableFuture.failedFuture(new BotPermissionException(Permission.MANAGE_WEBHOOKS));
		}

		return channel.createWebhook("Sx4 - Leaver").submit().thenCompose(webhook -> {
			WebhookClient webhookClient = new WebhookClient(webhook.getIdLong(), webhook.getToken(), this.executor, this.client);

			this.webhooks.put(channel.getIdLong(), webhookClient);

			Bson update = Updates.combine(
				Updates.set("leaver.webhook.id", webhook.getIdLong()),
				Updates.set("leaver.webhook.token", webhook.getToken())
			);

			return this.bot.getMongo().updateGuildById(channel.getGuild().getIdLong(), update)
				.thenCompose(result -> webhookClient.send(message))
				.thenApply(webhookMessage -> new ReadonlyMessage(webhookMessage, webhook.getIdLong(), webhook.getToken()));
		}).exceptionallyCompose(exception -> {
			Throwable cause = exception instanceof CompletionException ? exception.getCause() : exception;
			if (cause instanceof HttpException && ((HttpException) cause).getCode() == 404) {
				this.webhooks.remove(channel.getIdLong());

				return this.createWebhook(channel, message);
			}

			return CompletableFuture.failedFuture(exception);
		});
	}

	public CompletableFuture<ReadonlyMessage> sendLeaver(BaseGuildMessageChannel channel, Document webhookData, WebhookMessage message) {
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
				Throwable cause = exception instanceof CompletionException ? exception.getCause() : exception;
				if (cause instanceof HttpException && ((HttpException) cause).getCode() == 404) {
					this.webhooks.remove(channel.getIdLong());

					return this.createWebhook(channel, message);
				}

				return CompletableFuture.failedFuture(exception);
			});
	}

}
