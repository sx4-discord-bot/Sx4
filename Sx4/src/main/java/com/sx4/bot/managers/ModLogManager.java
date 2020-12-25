package com.sx4.bot.managers;

import club.minnced.discord.webhook.WebhookClient;
import club.minnced.discord.webhook.WebhookClientBuilder;
import club.minnced.discord.webhook.exception.HttpException;
import club.minnced.discord.webhook.receive.ReadonlyMessage;
import club.minnced.discord.webhook.send.WebhookEmbed;
import club.minnced.discord.webhook.send.WebhookMessage;
import club.minnced.discord.webhook.send.WebhookMessageBuilder;
import com.mongodb.client.model.Updates;
import com.sx4.bot.core.Sx4;
import com.sx4.bot.database.Database;
import com.sx4.bot.managers.impl.WebhookManagerImpl;
import com.sx4.bot.utility.ExceptionUtility;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import okhttp3.OkHttpClient;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;

public class ModLogManager implements WebhookManagerImpl {

	private static final ModLogManager INSTANCE = new ModLogManager();

	public static ModLogManager get() {
		return ModLogManager.INSTANCE;
	}

	private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
	private final OkHttpClient client = new OkHttpClient();

	private final Map<Long, WebhookClient> webhooks;

	private ModLogManager() {
		this.webhooks = new HashMap<>();
	}

	public WebhookClient getWebhook(long channelId) {
		return this.webhooks.get(channelId);
	}

	public WebhookClient removeWebhook(long channelId) {
		return this.webhooks.remove(channelId);
	}

	public void putWebhook(long channelId, WebhookClient webhook) {
		this.webhooks.put(channelId, webhook);
	}

	private void createWebhook(TextChannel channel, WebhookMessage message, Consumer<ReadonlyMessage> consumer) {
		if (!channel.getGuild().getSelfMember().hasPermission(channel, Permission.MANAGE_WEBHOOKS)) {
			return;
		}

		channel.createWebhook("Sx4 - Mod Logs").queue(webhook -> {
			WebhookClient webhookClient = new WebhookClientBuilder(webhook.getUrl())
				.setExecutorService(this.executor)
				.setHttpClient(this.client)
				.build();

			this.webhooks.put(channel.getIdLong(), webhookClient);

			Bson update = Updates.combine(
				Updates.set("modLog.webhook.id", webhook.getIdLong()),
				Updates.set("modLog.webhook.token", webhook.getToken())
			);

			Database.get().updateGuildById(channel.getGuild().getIdLong(), update)
				.thenCompose(result -> webhookClient.send(message))
				.whenComplete((webhookMessage, exception) -> {
					if (exception instanceof HttpException && ((HttpException) exception).getCode() == 404) {
						this.webhooks.remove(channel.getIdLong());

						this.createWebhook(channel, message, consumer);

						return;
					} else if (ExceptionUtility.sendErrorMessage(exception)) {
						return;
					}

					consumer.accept(webhookMessage);
				});
		});
	}

	public void sendModLog(TextChannel channel, Document webhookData, WebhookEmbed embed, Consumer<ReadonlyMessage> consumer) {
		User selfUser = channel.getJDA().getSelfUser();

		WebhookMessage message = new WebhookMessageBuilder()
			.setAvatarUrl(webhookData.get("avatar", selfUser.getEffectiveAvatarUrl()))
			.setUsername(webhookData.get("name", "Sx4 - Mod Logs"))
			.addEmbeds(embed)
			.build();

		long webhookId = webhookData.get("id", 0L);
		String webhookToken = webhookData.getString("token");

		WebhookClient webhook;
		if (this.webhooks.containsKey(channel.getIdLong())) {
			webhook = this.webhooks.get(channel.getIdLong());
		} else if (webhookId == 0L) {
			this.createWebhook(channel, message, consumer);

			return;
		} else {
			webhook = new WebhookClientBuilder(webhookId, webhookToken)
				.setExecutorService(this.executor)
				.setHttpClient(this.client)
				.build();

			this.webhooks.put(channel.getIdLong(), webhook);
		}

		webhook.send(message).whenComplete((webhookMessage, exception) -> {
			if (exception instanceof HttpException && ((HttpException) exception).getCode() == 404) {
				this.webhooks.remove(channel.getIdLong());

				this.createWebhook(channel, message, consumer);

				return;
			} else if (ExceptionUtility.sendErrorMessage(exception)) {
				return;
			}

			consumer.accept(webhookMessage);
		});
	}

	public void editModLog(long messageId, long channelId, Document webhookData, WebhookEmbed embed) {
		User selfUser = Sx4.get().getShardManager().getShardById(0).getSelfUser();

		WebhookMessage message = new WebhookMessageBuilder()
			.setAvatarUrl(webhookData.get("url", selfUser.getEffectiveAvatarUrl()))
			.setUsername(webhookData.get("name", selfUser.getName()))
			.addEmbeds(embed)
			.build();

		WebhookClient webhook;
		if (this.webhooks.containsKey(channelId)) {
			webhook = this.webhooks.get(channelId);
		} else if (!webhookData.containsKey("id")) {
			return;
		} else {
			webhook = new WebhookClientBuilder(webhookData.getLong("id"), webhookData.getString("token"))
				.setExecutorService(this.executor)
				.setHttpClient(this.client)
				.build();

			this.webhooks.put(channelId, webhook);
		}

		webhook.edit(messageId, message).whenComplete((webhookMessage, exception) -> {
			if (!(exception instanceof HttpException)) {
				ExceptionUtility.sendErrorMessage(exception);
			}
		});
	}

	public void deleteModLog(long messageId, long channelId, Document webhookData) {
		WebhookClient webhook;
		if (this.webhooks.containsKey(channelId)) {
			webhook = this.webhooks.get(channelId);
		} else if (!webhookData.containsKey("id")) {
			return;
		} else {
			webhook = new WebhookClientBuilder(webhookData.getLong("id"), webhookData.getString("token"))
				.setExecutorService(this.executor)
				.setHttpClient(this.client)
				.build();

			this.webhooks.put(channelId, webhook);
		}

		webhook.delete(messageId).whenComplete((webhookMessage, exception) -> {
			if (!(exception instanceof HttpException)) {
				ExceptionUtility.sendErrorMessage(exception);
			}
		});
	}

}