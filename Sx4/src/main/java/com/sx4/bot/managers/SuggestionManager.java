package com.sx4.bot.managers;

import club.minnced.discord.webhook.WebhookClient;
import club.minnced.discord.webhook.WebhookClientBuilder;
import club.minnced.discord.webhook.exception.HttpException;
import club.minnced.discord.webhook.receive.ReadonlyMessage;
import club.minnced.discord.webhook.send.WebhookEmbed;
import club.minnced.discord.webhook.send.WebhookMessage;
import club.minnced.discord.webhook.send.WebhookMessageBuilder;
import com.mongodb.client.model.Updates;
import com.sx4.bot.database.Database;
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

public class SuggestionManager {

	private static final SuggestionManager INSTANCE = new SuggestionManager();

	public static SuggestionManager get() {
		return SuggestionManager.INSTANCE;
	}

	private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
	private final OkHttpClient client = new OkHttpClient();

	private final Map<Long, WebhookClient> webhooks;

	private SuggestionManager() {
		this.webhooks = new HashMap<>();
	}

	public WebhookClient removeWebhook(long channelId) {
		return this.webhooks.remove(channelId);
	}

	private void createWebhook(TextChannel channel, WebhookMessage message) {
		channel.createWebhook("Sx4 - Suggestions").queue(webhook -> {
			WebhookClient webhookClient = new WebhookClientBuilder(webhook.getUrl())
				.setExecutorService(this.executor)
				.setHttpClient(this.client)
				.build();

			this.webhooks.put(channel.getIdLong(), webhookClient);

			Bson update = Updates.combine(
				Updates.set("suggestion.webhook.id", webhook.getIdLong()),
				Updates.set("suggestion.webhook.token", webhook.getToken())
			);

			Database.get().updateGuildById(channel.getGuild().getIdLong(), update)
				.thenCompose(result -> webhookClient.send(message))
				.whenComplete((result, exception) -> {
					if (exception instanceof HttpException && ((HttpException) exception).getCode() == 404) {
						this.webhooks.remove(channel.getIdLong());

						if (channel.getGuild().getSelfMember().hasPermission(channel, Permission.MANAGE_WEBHOOKS)) {
							this.createWebhook(channel, message);
						}
					} else {
						ExceptionUtility.sendErrorMessage(exception);
					}
				});
		});
	}

	public void sendSuggestion(TextChannel channel, Document webhookData, WebhookEmbed embed, Consumer<ReadonlyMessage> consumer) {
		User selfUser = channel.getJDA().getSelfUser();

		WebhookMessage message = new WebhookMessageBuilder()
			.setAvatarUrl(webhookData.get("url", selfUser.getEffectiveAvatarUrl()))
			.setUsername(webhookData.get("name", selfUser.getName()))
			.addEmbeds(embed)
			.build();

		WebhookClient webhook;
		if (this.webhooks.containsKey(channel.getIdLong())) {
			webhook = this.webhooks.get(channel.getIdLong());
		} else if (!webhookData.containsKey("id")) {
			if (channel.getGuild().getSelfMember().hasPermission(channel, Permission.MANAGE_WEBHOOKS)) {
				this.createWebhook(channel, message);
			}

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

				if (channel.getGuild().getSelfMember().hasPermission(channel, Permission.MANAGE_WEBHOOKS)) {
					this.createWebhook(channel, message);
				}

				return;
			} else if (ExceptionUtility.sendErrorMessage(exception)) {
				return;
			}

			consumer.accept(webhookMessage);
		});
	}

	public void editSuggestion(long messageId, TextChannel channel, Document webhookData, WebhookEmbed embed, Consumer<ReadonlyMessage> consumer) {
		User selfUser = channel.getJDA().getSelfUser();

		WebhookMessage message = new WebhookMessageBuilder()
			.setAvatarUrl(webhookData.get("url", selfUser.getEffectiveAvatarUrl()))
			.setUsername(webhookData.get("name", selfUser.getName()))
			.addEmbeds(embed)
			.build();

		WebhookClient webhook;
		if (this.webhooks.containsKey(channel.getIdLong())) {
			webhook = this.webhooks.get(channel.getIdLong());
		} else if (!webhookData.containsKey("id")) {
			return;
		} else {
			webhook = new WebhookClientBuilder(webhookData.getLong("id"), webhookData.getString("token"))
				.setExecutorService(this.executor)
				.setHttpClient(this.client)
				.build();

			this.webhooks.put(channel.getIdLong(), webhook);
		}

		webhook.edit(messageId, message).whenComplete((webhookMessage, exception) -> {
			if (exception instanceof HttpException) {
				HttpException httpException = ((HttpException) exception);
				if (httpException.getCode() == 404) {
					Document body = Document.parse(httpException.getBody());

					int code = body.get("code", 0);
					if (code == 10015) {
						this.webhooks.remove(channel.getIdLong());
					}
				}

				return;
			}

			if (ExceptionUtility.sendErrorMessage(exception)) {
				return;
			}

			consumer.accept(webhookMessage);
		});
	}

}
