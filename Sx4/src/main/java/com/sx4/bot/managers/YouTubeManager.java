package com.sx4.bot.managers;

import com.mongodb.client.model.*;
import com.sx4.bot.core.Sx4;
import com.sx4.bot.database.mongo.MongoDatabase;
import com.sx4.bot.entities.webhook.SentWebhookMessage;
import com.sx4.bot.entities.webhook.WebhookChannel;
import com.sx4.bot.events.youtube.*;
import com.sx4.bot.exceptions.mod.BotPermissionException;
import com.sx4.bot.hooks.YouTubeListener;
import com.sx4.bot.http.HttpCallback;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.WebhookClient;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import okhttp3.MultipartBody;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.time.Clock;
import java.util.*;
import java.util.concurrent.*;

public class YouTubeManager implements WebhookManager {
	
	public static final Document DEFAULT_MESSAGE = new Document("content", "**[{channel.name}]({channel.url})** just uploaded a new video!\n{video.url}");
	
	private final List<YouTubeListener> listeners;

	private final Map<String, ScheduledFuture<?>> executors;

	private final HashMap<Long, WebhookClient<Message>> webhooks;
	
	private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

	private final Sx4 bot;
	
	public YouTubeManager(Sx4 bot) {
		this.executors = new HashMap<>();
		this.listeners = new ArrayList<>();
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
	
	public YouTubeManager addListener(YouTubeListener... listeners) {
		this.listeners.addAll(Arrays.asList(listeners));
		
		return this;
	}
	
	public YouTubeManager removeListener(YouTubeListener... listeners) {
		this.listeners.removeAll(Arrays.asList(listeners));
		
		return this;
	}
	
	public void onYouTube(YouTubeEvent event) {
		for (YouTubeListener listener : this.listeners) {
			listener.onYouTube(event);
			
			if (event instanceof YouTubeUpdateEvent) {
				listener.onYouTubeUpdate((YouTubeUpdateEvent) event);

				if (event instanceof YouTubeUpdateTitleEvent) {
					listener.onYouTubeUpdateTitle((YouTubeUpdateTitleEvent) event);
				}

				if (event instanceof YouTubeUploadEvent) {
					listener.onYouTubeUpload((YouTubeUploadEvent) event);
				}
			}
			
			if (event instanceof YouTubeDeleteEvent) {
				listener.onYouTubeDelete((YouTubeDeleteEvent) event);
			}
		}
	}
	
	public ScheduledExecutorService getExecutor() {
		return this.executor;
	}
	
	public boolean hasExecutor(String channelId) {
		return this.executors.containsKey(channelId);
	}
	
	public ScheduledFuture<?> getExecutor(String channelId) {
		return this.executors.get(channelId);
	}
	
	public void putExecutor(String channelId, ScheduledFuture<?> executor) {
		this.executors.put(channelId, executor);
	}
	
	public void deleteExecutor(String channelId) {
		ScheduledFuture<?> executor = this.executors.remove(channelId);
		if (executor != null && !executor.isDone()) {
			executor.cancel(true);
		}
	}
	
	public void putResubscription(String channelId, long seconds) {
		ScheduledFuture<?> executor = this.getExecutor(channelId);
		if (executor != null && !executor.isDone()) {
			executor.cancel(true);
		}
		
		this.putExecutor(channelId, this.executor.schedule(() -> this.resubscribe(channelId), seconds, TimeUnit.SECONDS));
	}
	
	public DeleteOneModel<Document> resubscribeBulk(String channelId) {
		long amount = this.bot.getMongo().countYouTubeNotifications(Filters.eq("uploaderId", channelId), new CountOptions().limit(1));

		this.deleteExecutor(channelId);

		DeleteOneModel<Document> model = null;
		if (amount != 0) {
			RequestBody body = new MultipartBody.Builder()
				.addFormDataPart("hub.mode", "subscribe")
				.addFormDataPart("hub.topic", "https://www.youtube.com/xml/feeds/videos.xml?channel_id=" + channelId)
				.addFormDataPart("hub.callback", this.bot.getConfig().getBaseUrl() + "/api/youtube")
				.addFormDataPart("hub.verify", "sync")
				.addFormDataPart("hub.verify_token", this.bot.getConfig().getYouTube())
				.setType(MultipartBody.FORM)
				.build();
			
			Request request = new Request.Builder()
				.url("https://pubsubhubbub.appspot.com/subscribe")
				.post(body)
				.build();
			
			this.bot.getHttpClient().newCall(request).enqueue((HttpCallback) response -> {
				if (response.isSuccessful()) {
					System.out.println("Resubscribed to " + channelId + " for YouTube notifications");
				} else {
					System.err.printf("Failed to resubscribe to %s for YouTube notifications, Code: %d, Message: %s%n", channelId, response.code(), response.body().string());
				}
				
				response.close();
			});
		} else {
			model = new DeleteOneModel<>(Filters.eq("_id", channelId));
			this.deleteExecutor(channelId);
		}
		
		return model;
	}
	
	public void resubscribe(String channelId) {
		DeleteOneModel<Document> model = this.resubscribeBulk(channelId);
		if (model != null) {
			this.bot.getMongo().deleteYouTubeSubscription(model.getFilter()).whenComplete(MongoDatabase.exceptionally());
		}
	}
	
	public void ensureSubscriptions() {
		List<WriteModel<Document>> bulkData = new ArrayList<>();
		
		this.bot.getMongo().getYouTubeSubscriptions().find().forEach(data -> {
			String channelId = data.getString("_id");
			
			long timeTill = data.getLong("resubscribeAt") - Clock.systemUTC().instant().getEpochSecond();
			if (timeTill <= 0) { 
				DeleteOneModel<Document> model = this.resubscribeBulk(channelId);
				if (model != null) {
					bulkData.add(model);
				}
			} else {
				this.putResubscription(channelId, timeTill);
			}
		});
		
		if (!bulkData.isEmpty()) {
			this.bot.getMongo().bulkWriteYouTubeSubscriptions(bulkData).whenComplete(MongoDatabase.exceptionally());
		}
	}

	private void disableYouTubeNotifications(Channel channel) {
		Bson update = Updates.combine(
			Updates.unset("webhook.id"),
			Updates.unset("webhook.token"),
			Updates.set("enabled", false)
		);

		this.bot.getMongo().updateManyYouTubeNotifications(Filters.eq("channelId", channel.getIdLong()), update).whenComplete(MongoDatabase.exceptionally());
	}

	private CompletableFuture<SentWebhookMessage> createWebhook(WebhookChannel channel, Document webhookData, MessageCreateData message, boolean premium) {
		if (!channel.getGuild().getSelfMember().hasPermission(channel, Permission.MANAGE_WEBHOOKS)) {
			this.disableYouTubeNotifications(channel);
			return CompletableFuture.failedFuture(new BotPermissionException(Permission.MANAGE_WEBHOOKS));
		}

		return channel.createWebhook("Sx4 - YouTube").submit().thenCompose(webhook -> {
			this.webhooks.put(channel.getIdLong(), webhook);

			Bson update = Updates.combine(
				Updates.set("webhook.id", webhook.getIdLong()),
				Updates.set("webhook.token", webhook.getToken())
			);

			long webhookChannelId = channel.getWebhookChannel().getIdLong();

			WebhookMessageCreateAction<Message> action = webhook.sendMessage(message)
				.setAvatarUrl(premium ? webhookData.get("avatar", this.bot.getConfig().getYouTubeAvatar()) : this.bot.getConfig().getYouTubeAvatar())
				.setUsername(premium ? webhookData.get("name", "Sx4 - YouTube") : "Sx4 - YouTube");

			return this.bot.getMongo().updateManyYouTubeNotifications(Filters.or(Filters.eq("channelId", webhookChannelId), Filters.eq("webhook.channelId", webhookChannelId)), update)
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

	public CompletableFuture<SentWebhookMessage> sendYouTubeNotification(WebhookChannel channel, Document webhookData, MessageCreateData message, boolean premium) {
		long channelId = channel.getIdLong();

		WebhookClient<Message> webhook;
		if (this.webhooks.containsKey(channelId)) {
			webhook = this.webhooks.get(channelId);
		} else if (!webhookData.containsKey("id")) {
			return this.createWebhook(channel, webhookData, message, premium);
		} else {
			webhook = WebhookClient.createClient(channel.getJDA(), Long.toString(webhookData.getLong("id")), webhookData.getString("token"));

			this.webhooks.put(channelId, webhook);
		}

		WebhookMessageCreateAction<Message> action = webhook.sendMessage(message)
			.setAvatarUrl(premium ? webhookData.get("avatar", this.bot.getConfig().getYouTubeAvatar()) : this.bot.getConfig().getYouTubeAvatar())
			.setUsername(premium ? webhookData.get("name", "Sx4 - YouTube") : "Sx4 - YouTube");

		return channel.sendWebhookMessage(action)
			.thenApply(webhookMessage -> new SentWebhookMessage(webhookMessage, webhook.getIdLong(), webhook.getToken()))
			.exceptionallyCompose(exception -> {
				Throwable cause = exception instanceof CompletionException ? exception.getCause() : exception;
				if (cause instanceof ErrorResponseException && ((ErrorResponseException) cause).getErrorCode() == 10015) {
					this.webhooks.remove(channelId);

					return this.createWebhook(channel, webhookData, message, premium);
				}

				return CompletableFuture.failedFuture(exception);
			});
	}

}
