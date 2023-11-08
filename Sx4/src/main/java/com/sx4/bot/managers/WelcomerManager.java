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
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public class WelcomerManager implements WebhookManager {

	public static final Document DEFAULT_MESSAGE = new Document("content", "{user.mention}, Welcome to **{server.name}**. Enjoy your time here! The server now has {server.members} members.");

	private final Map<Long, WebhookClient<Message>> webhooks;

	private final Sx4 bot;

	public WelcomerManager(Sx4 bot) {
		this.webhooks = new HashMap<>();
		this.bot = bot;
	}

	public WebhookClient<Message> getWebhook(long channelId) {
		return this.webhooks.get(channelId);
	}

	public WebhookClient<Message> removeWebhook(long channelId) {
		return this.webhooks.remove(channelId);
	}

	public void putWebhook(long id, WebhookClient<Message> webhook) {
		this.webhooks.put(id, webhook);
	}

	private void disableWelcomer(Guild guild) {
		Bson update = Updates.combine(
			Updates.unset("welcomer.webhook.id"),
			Updates.unset("welcomer.webhook.token"),
			Updates.unset("welcomer.enabled")
		);

		this.bot.getMongo().updateGuildById(guild.getIdLong(), update).whenComplete(MongoDatabase.exceptionally());
	}

	private CompletableFuture<SentWebhookMessage> createWebhook(WebhookChannel channel, Document webhookData, MessageCreateData message, boolean premium) {
		if (!channel.getGuild().getSelfMember().hasPermission(channel, Permission.MANAGE_WEBHOOKS)) {
			this.disableWelcomer(channel.getGuild());
			return CompletableFuture.failedFuture(new BotPermissionException(Permission.MANAGE_WEBHOOKS));
		}

		return channel.createWebhook("Sx4 - Welcomer").submit().thenCompose(webhook -> {
			this.webhooks.put(channel.getIdLong(), webhook);

			Bson update = Updates.combine(
				Updates.set("welcomer.webhook.id", webhook.getIdLong()),
				Updates.set("welcomer.webhook.token", webhook.getToken())
			);

			WebhookMessageCreateAction<Message> action = webhook.sendMessage(message)
				.setUsername(premium ? webhookData.get("name", "Sx4 - Welcomer") : "Sx4 - Welcomer")
				.setAvatarUrl(premium ? webhookData.get("avatar", channel.getJDA().getSelfUser().getEffectiveAvatarUrl()) : channel.getJDA().getSelfUser().getEffectiveAvatarUrl());

			return this.bot.getMongo().updateGuildById(channel.getGuild().getIdLong(), update)
				.thenCompose(result -> channel.sendWebhookMessage(action))
				.thenApply(webhookMessage -> new SentWebhookMessage(webhookMessage, webhook.getIdLong(), webhook.getToken()));
		}).exceptionallyCompose(exception -> {
			Throwable cause = exception instanceof CompletionException ? exception.getCause() : exception;
			if (cause instanceof ErrorResponseException && ((ErrorResponseException) cause).getErrorCode() == 10015) {
				this.webhooks.remove(channel.getIdLong());

				return this.createWebhook(channel, webhookData, message, premium);
			}

			return CompletableFuture.failedFuture(exception);
		});
	}

	public CompletableFuture<SentWebhookMessage> sendWelcomer(WebhookChannel channel, Document webhookData, MessageCreateData message, boolean premium) {
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
			.setUsername(premium ? webhookData.get("name", "Sx4 - Welcomer") : "Sx4 - Welcomer")
			.setAvatarUrl(premium ? webhookData.get("avatar", channel.getJDA().getSelfUser().getEffectiveAvatarUrl()) : channel.getJDA().getSelfUser().getEffectiveAvatarUrl());

		return channel.sendWebhookMessage(action)
			.thenApply(WebhookMessage -> new SentWebhookMessage(WebhookMessage, webhook.getIdLong(), webhook.getToken()))
			.exceptionallyCompose(exception -> {
				Throwable cause = exception instanceof CompletionException ? exception.getCause() : exception;
				if (cause instanceof ErrorResponseException && ((ErrorResponseException) cause).getErrorCode() == 10015) {
					this.webhooks.remove(channel.getIdLong());

					return this.createWebhook(channel, webhookData, message, premium);
				}

				return CompletableFuture.failedFuture(exception);
			});
	}

}
