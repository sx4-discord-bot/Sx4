package com.sx4.bot.managers;

import club.minnced.discord.webhook.WebhookClient;
import club.minnced.discord.webhook.WebhookClientBuilder;
import club.minnced.discord.webhook.exception.HttpException;
import club.minnced.discord.webhook.send.WebhookMessage;
import com.mongodb.client.model.Updates;
import com.sx4.bot.database.Database;
import com.sx4.bot.utility.ExceptionUtility;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.TextChannel;
import okhttp3.OkHttpClient;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class LeaverManager implements WebhookManager {

	private static final LeaverManager INSTANCE = new LeaverManager();

	public static LeaverManager get() {
		return LeaverManager.INSTANCE;
	}

	public static final Document DEFAULT_MESSAGE = new Document("content", "**{user.name}** has just left **{server.name}**. Bye **{user.name}**!");

	private final Map<Long, WebhookClient> webhooks;

	private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
	private final OkHttpClient client = new OkHttpClient();

	private LeaverManager() {
		this.webhooks = new HashMap<>();
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

	private void createWebhook(TextChannel channel, WebhookMessage message) {
		if (!channel.getGuild().getSelfMember().hasPermission(channel, Permission.MANAGE_WEBHOOKS)) {
			return;
		}

		channel.createWebhook("Sx4 - Leaver").queue(webhook -> {
			WebhookClient webhookClient = new WebhookClientBuilder(webhook.getUrl())
				.setExecutorService(this.executor)
				.setHttpClient(this.client)
				.build();

			this.webhooks.put(channel.getIdLong(), webhookClient);

			Bson update = Updates.combine(
				Updates.set("leaver.webhook.id", webhook.getIdLong()),
				Updates.set("leaver.webhook.token", webhook.getToken())
			);

			Database.get().updateGuildById(channel.getGuild().getIdLong(), update)
				.thenCompose(result -> webhookClient.send(message))
				.whenComplete((webhookMessage, exception) -> {
					if (exception instanceof HttpException && ((HttpException) exception).getCode() == 404) {
						this.webhooks.remove(channel.getIdLong());

						this.createWebhook(channel, message);

						return;
					}

					ExceptionUtility.sendErrorMessage(exception);
				});
		});
	}

	public void sendLeaver(TextChannel channel, Document webhookData, WebhookMessage message) {
		WebhookClient webhook;
		if (this.webhooks.containsKey(channel.getIdLong())) {
			webhook = this.webhooks.get(channel.getIdLong());
		} else if (!webhookData.containsKey("id")) {
			this.createWebhook(channel, message);

			return;
		} else {
			webhook = new WebhookClientBuilder(webhookData.getLong("id"), webhookData.getString("token"))
				.setExecutorService(this.executor)
				.setHttpClient(this.client)
				.build();

			this.webhooks.put(channel.getIdLong(), webhook);
		}

		webhook.send(message).whenComplete((webhookMessage, exception) -> {
			if (exception instanceof HttpException && ((HttpException) exception).getCode() == 404) {
				this.webhooks.remove(channel.getIdLong());

				this.createWebhook(channel, message);

				return;
			}

			ExceptionUtility.sendErrorMessage(exception);
		});
	}

}
