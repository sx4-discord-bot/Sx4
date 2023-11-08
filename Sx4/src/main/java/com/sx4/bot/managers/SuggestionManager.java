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
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.WebhookClient;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public class SuggestionManager implements WebhookManager {

	private final Map<Long, WebhookClient<Message>> webhooks;

	private final Sx4 bot;

	public SuggestionManager(Sx4 bot) {
		this.webhooks = new HashMap<>();
		this.bot = bot;
	}

	public WebhookClient<Message> getWebhook(long channelId) {
		return this.webhooks.get(channelId);
	}

	public WebhookClient<Message> removeWebhook(long channelId) {
		return this.webhooks.remove(channelId);
	}

	public void putWebhook(long channelId, WebhookClient<Message> webhook) {
		this.webhooks.put(channelId, webhook);
	}

	private void disableSuggestion(Guild guild) {
		Bson update = Updates.combine(
			Updates.unset("suggestion.webhook.id"),
			Updates.unset("suggestion.webhook.token"),
			Updates.unset("suggestion.enabled")
		);

		this.bot.getMongo().updateGuildById(guild.getIdLong(), update).whenComplete(MongoDatabase.exceptionally());
	}

	private CompletableFuture<SentWebhookMessage> createWebhook(WebhookChannel channel, Document webhookData, MessageCreateData message, boolean premium) {
		if (!channel.getGuild().getSelfMember().hasPermission(channel, Permission.MANAGE_WEBHOOKS)) {
			this.disableSuggestion(channel.getGuild());
			return CompletableFuture.failedFuture(new BotPermissionException(Permission.MANAGE_WEBHOOKS));
		}

		return channel.createWebhook("Sx4 - Suggestions").submit().thenCompose(webhook -> {
			this.webhooks.put(channel.getIdLong(), webhook);

			Bson update = Updates.combine(
				Updates.set("suggestion.webhook.id", webhook.getIdLong()),
				Updates.set("suggestion.webhook.token", webhook.getToken())
			);

			WebhookMessageCreateAction<Message> action = webhook.sendMessage(message)
				.setAvatarUrl(premium ? webhookData.get("avatar", channel.getJDA().getSelfUser().getEffectiveAvatarUrl()) : channel.getJDA().getSelfUser().getEffectiveAvatarUrl())
				.setUsername(premium ? webhookData.get("name", "Sx4 - Suggestions") : "Sx4 - Suggestions");

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

	public CompletableFuture<SentWebhookMessage> sendSuggestion(WebhookChannel channel, Document webhookData, boolean premium, MessageEmbed embed) {
		MessageCreateData message = new MessageCreateBuilder()
			.addEmbeds(embed)
			.build();

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
			.setAvatarUrl(premium ? webhookData.get("avatar", channel.getJDA().getSelfUser().getEffectiveAvatarUrl()) : channel.getJDA().getSelfUser().getEffectiveAvatarUrl())
			.setUsername(premium ? webhookData.get("name", "Sx4 - Suggestions") : "Sx4 - Suggestions");

		return channel.sendWebhookMessage(action)
			.thenApply(webhookMessage -> new SentWebhookMessage(webhookMessage, webhook.getIdLong(), webhook.getToken()))
			.exceptionallyCompose(exception -> {
				Throwable cause = exception instanceof CompletionException ? exception.getCause() : exception;
				if (cause instanceof ErrorResponseException && ((ErrorResponseException) cause).getErrorCode() == 10015) {
					this.webhooks.remove(channel.getIdLong());

					return this.createWebhook(channel, webhookData, message, premium);
				}

				return CompletableFuture.failedFuture(exception);
			});
	}

	public CompletableFuture<SentWebhookMessage> editSuggestion(long messageId, WebhookChannel channel, Document webhookData, MessageEmbed embed) {
		MessageEditData message = new MessageEditBuilder()
			.setEmbeds(embed)
			.build();

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

		return channel.editWebhookMessage(webhook.editMessageById(messageId, message)).thenApply(webhookMessage -> new SentWebhookMessage(webhookMessage, webhook.getIdLong(), webhook.getToken()));
	}

}
