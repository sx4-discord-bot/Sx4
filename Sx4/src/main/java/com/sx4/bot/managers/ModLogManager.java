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
import com.sx4.bot.exceptions.mod.BotPermissionException;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import okhttp3.OkHttpClient;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class ModLogManager implements WebhookManager {

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

	private CompletableFuture<ReadonlyMessage> createWebhook(TextChannel channel, WebhookMessage message) {
		if (!channel.getGuild().getSelfMember().hasPermission(channel, Permission.MANAGE_WEBHOOKS)) {
			return CompletableFuture.failedFuture(new BotPermissionException(Permission.MANAGE_WEBHOOKS));
		}

		return channel.createWebhook("Sx4 - Mod Logs").submit().thenCompose(webhook -> {
			WebhookClient webhookClient = new WebhookClientBuilder(webhook.getUrl())
				.setExecutorService(this.executor)
				.setHttpClient(this.client)
				.build();

			this.webhooks.put(channel.getIdLong(), webhookClient);

			Bson update = Updates.combine(
				Updates.set("modLog.webhook.id", webhook.getIdLong()),
				Updates.set("modLog.webhook.token", webhook.getToken())
			);

			return Database.get().updateGuildById(channel.getGuild().getIdLong(), update).thenCompose(result -> webhookClient.send(message));
		}).exceptionallyCompose(exception -> {
			if (exception instanceof HttpException && ((HttpException) exception).getCode() == 404) {
				this.webhooks.remove(channel.getIdLong());

				return this.createWebhook(channel, message);
			}

			return CompletableFuture.failedFuture(exception);
		});
	}

	public CompletableFuture<ReadonlyMessage> sendModLog(TextChannel channel, Document webhookData, WebhookEmbed embed) {
		User selfUser = channel.getJDA().getSelfUser();

		WebhookMessage message = new WebhookMessageBuilder()
			.setAvatarUrl(webhookData.get("avatar", selfUser.getEffectiveAvatarUrl()))
			.setUsername(webhookData.get("name", "Sx4 - Mod Logs"))
			.addEmbeds(embed)
			.build();

		WebhookClient webhook;
		if (this.webhooks.containsKey(channel.getIdLong())) {
			webhook = this.webhooks.get(channel.getIdLong());
		} else if (!webhookData.containsKey("id")) {
			return this.createWebhook(channel, message);
		} else {
			webhook = new WebhookClientBuilder(webhookData.getLong("id"), webhookData.getString("token"))
				.setExecutorService(this.executor)
				.setHttpClient(this.client)
				.build();

			this.webhooks.put(channel.getIdLong(), webhook);
		}

		return webhook.send(message).exceptionallyCompose(exception -> {
			if (exception instanceof HttpException && ((HttpException) exception).getCode() == 404) {
				this.webhooks.remove(channel.getIdLong());

				return this.createWebhook(channel, message);
			}

			return CompletableFuture.failedFuture(exception);
		});
	}

	public CompletableFuture<ReadonlyMessage> editModLog(long messageId, long channelId, Document webhookData, WebhookEmbed embed) {
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
			return CompletableFuture.completedFuture(null);
		} else {
			webhook = new WebhookClientBuilder(webhookData.getLong("id"), webhookData.getString("token"))
				.setExecutorService(this.executor)
				.setHttpClient(this.client)
				.build();

			this.webhooks.put(channelId, webhook);
		}

		return webhook.edit(messageId, message);
	}

}