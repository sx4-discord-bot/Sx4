package com.sx4.bot.managers;

import club.minnced.discord.webhook.exception.HttpException;
import club.minnced.discord.webhook.send.WebhookMessage;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndDeleteOptions;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Updates;
import com.sx4.bot.core.Sx4;
import com.sx4.bot.entities.twitch.TwitchSubscriptionType;
import com.sx4.bot.entities.webhook.ReadonlyMessage;
import com.sx4.bot.entities.webhook.WebhookChannel;
import com.sx4.bot.entities.webhook.WebhookClient;
import com.sx4.bot.events.twitch.TwitchEvent;
import com.sx4.bot.events.twitch.TwitchStreamStartEvent;
import com.sx4.bot.exceptions.mod.BotPermissionException;
import com.sx4.bot.hooks.TwitchListener;
import com.sx4.bot.http.HttpCallback;
import net.dv8tion.jda.api.Permission;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class TwitchManager {

	public static Document DEFAULT_MESSAGE = new Document("embed",
		new Document("title", "{streamer.name} is now live!")
			.append("url", "{streamer.url}")
			.append("description", "{stream.title}")
			.append("color", 0x9146FF)
			.append("image", new Document("url", "{stream.preview}"))
			.append("timestamp", "{stream.start}")
			.append("fields", List.of(
				new Document("name", "Game").append("value", "{stream.game}").append("inline", true)
			))
	);

	private final Sx4 bot;

	private final List<TwitchListener> listeners;

	private final Map<Long, WebhookClient> webhooks;

	private final OkHttpClient client = new OkHttpClient();
	private final ScheduledExecutorService scheduledExecutor = Executors.newSingleThreadScheduledExecutor();

	public TwitchManager(Sx4 bot) {
		this.listeners = new ArrayList<>();
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

	public TwitchManager addListener(TwitchListener... listeners) {
		this.listeners.addAll(Arrays.asList(listeners));

		return this;
	}

	public TwitchManager removeListener(TwitchListener... listeners) {
		this.listeners.removeAll(Arrays.asList(listeners));

		return this;
	}

	public void onEvent(TwitchEvent event) {
		for (TwitchListener listener : this.listeners) {
			listener.onEvent(event);

			if (event instanceof TwitchStreamStartEvent) {
				listener.onStreamStart((TwitchStreamStartEvent) event);
			}
		}
	}

	public void unsubscribe(String streamerId) {
		FindOneAndDeleteOptions options = new FindOneAndDeleteOptions().projection(Projections.include("subscriptionId"));
		this.bot.getMongo().findAndDeleteTwitchSubscription(Filters.eq("streamerId", streamerId), options).whenComplete((data, exception) -> {
			if (data == null) {
				return;
			}

			Request request = new Request.Builder()
				.url("https://api.twitch.tv/helix/eventsub/subscriptions?id=" + data.getString("subscriptionId"))
				.delete()
				.addHeader("Authorization", "Bearer " + this.bot.getTwitchConfig().getToken())
				.addHeader("Client-Id", this.bot.getConfig().getTwitchClientId())
				.build();

			this.bot.getHttpClient().newCall(request).enqueue((HttpCallback) response -> {
				if (!response.isSuccessful()) {
					System.err.printf("Failed to unsubscribe to %s for Twitch notifications, Code: %d, Message: %s%n", streamerId, response.code(), response.body().string());
				} else {
					System.out.println("Unsubscribed from " + streamerId + " for Twitch notifications");
				}

				response.close();
			});
		});
	}

	public void subscribe(String streamerId, TwitchSubscriptionType type) {
		Document transport = new Document("method", "webhook")
			.append("callback", this.bot.getConfig().getBaseUrl() + "/api/twitch")
			.append("secret", this.bot.getConfig().getTwitchEventSecret());

		Document body = new Document("type", type.getIdentifier())
			.append("version", "1")
			.append("condition", new Document("broadcaster_user_id", streamerId))
			.append("transport", transport);

		Request request = new Request.Builder()
			.url("https://api.twitch.tv/helix/eventsub/subscriptions")
			.post(RequestBody.create(MediaType.parse("application/json"), body.toJson()))
			.addHeader("Authorization", "Bearer " + this.bot.getTwitchConfig().getToken())
			.addHeader("Client-Id", this.bot.getConfig().getTwitchClientId())
			.build();

		this.bot.getHttpClient().newCall(request).enqueue((HttpCallback) response -> {
			if (!response.isSuccessful()) {
				System.err.printf("Failed to resubscribe to %s for Twitch notifications, Code: %d, Message: %s%n", streamerId, response.code(), response.body().string());
			} else {
				System.out.println("Subscribed to " + streamerId + " for Twitch notifications");
			}

			response.close();
		});
	}

	public void subscribe(String streamerId) {
		this.subscribe(streamerId, TwitchSubscriptionType.ONLINE);
	}

	private CompletableFuture<ReadonlyMessage> createWebhook(WebhookChannel channel, WebhookMessage message) {
		if (!channel.getGuild().getSelfMember().hasPermission(channel, Permission.MANAGE_WEBHOOKS)) {
			return CompletableFuture.failedFuture(new BotPermissionException(Permission.MANAGE_WEBHOOKS));
		}

		return channel.createWebhook("Sx4 - Twitch").submit().thenCompose(webhook -> {
			WebhookClient webhookClient = channel.getWebhookClient(webhook.getIdLong(), webhook.getToken(), this.scheduledExecutor, this.client);

			this.webhooks.put(channel.getIdLong(), webhookClient);

			Bson update = Updates.combine(
				Updates.set("webhook.id", webhook.getIdLong()),
				Updates.set("webhook.token", webhook.getToken())
			);

			return this.bot.getMongo().updateManyTwitchNotifications(Filters.eq("channelId", channel.getIdLong()), update)
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

	public CompletableFuture<ReadonlyMessage> sendTwitchNotification(WebhookChannel channel, Document webhookData, WebhookMessage message) {
		long channelId = channel.getIdLong();

		WebhookClient webhook;
		if (this.webhooks.containsKey(channelId)) {
			webhook = this.webhooks.get(channelId);
		} else if (!webhookData.containsKey("id")) {
			return this.createWebhook(channel, message);
		} else {
			webhook = channel.getWebhookClient(webhookData.getLong("id"), webhookData.getString("token"), this.scheduledExecutor, this.client);

			this.webhooks.put(channelId, webhook);
		}

		return webhook.send(message)
			.thenApply(webhookMessage -> new ReadonlyMessage(webhookMessage, webhook.getId(), webhook.getToken()))
			.exceptionallyCompose(exception -> {
				Throwable cause = exception instanceof CompletionException ? exception.getCause() : exception;
				if (cause instanceof HttpException && ((HttpException) cause).getCode() == 404) {
					this.webhooks.remove(channelId);

					return this.createWebhook(channel, message);
				}

				return CompletableFuture.failedFuture(exception);
			});
	}

}
