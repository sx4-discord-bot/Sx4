package com.sx4.bot.handlers;

import club.minnced.discord.webhook.WebhookClient;
import club.minnced.discord.webhook.WebhookClientBuilder;
import club.minnced.discord.webhook.exception.HttpException;
import club.minnced.discord.webhook.send.WebhookMessage;
import club.minnced.discord.webhook.send.WebhookMessageBuilder;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.sx4.bot.core.Sx4;
import com.sx4.bot.database.mongo.MongoDatabase;
import com.sx4.bot.entities.youtube.YouTubeType;
import com.sx4.bot.events.youtube.YouTubeDeleteEvent;
import com.sx4.bot.events.youtube.YouTubeUpdateTitleEvent;
import com.sx4.bot.events.youtube.YouTubeUploadEvent;
import com.sx4.bot.formatter.JsonFormatter;
import com.sx4.bot.hooks.YouTubeListener;
import com.sx4.bot.managers.YouTubeManager;
import com.sx4.bot.utility.ExceptionUtility;
import com.sx4.bot.utility.MessageUtility;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.channel.text.TextChannelDeleteEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.sharding.ShardManager;
import okhttp3.OkHttpClient;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class YouTubeHandler implements YouTubeListener, EventListener {
	
	private final OkHttpClient client = new OkHttpClient();
	
	private final ScheduledExecutorService scheduledExecutor = Executors.newSingleThreadScheduledExecutor();
	
	private final ExecutorService notificationExecutor = Executors.newCachedThreadPool();
	
	private final Map<Long, WebhookClient> webhooks;

	private final Sx4 bot;

	public YouTubeHandler(Sx4 bot) {
		this.webhooks = new HashMap<>();
		this.bot = bot;
	}
	
	private WebhookMessageBuilder format(YouTubeUploadEvent event, Document document) {
		Document formattedDocument = new JsonFormatter(document)
			.addVariable("channel", event.getChannel())
			.addVariable("video", event.getVideo())
			.parse();

		return MessageUtility.fromJson(formattedDocument);
	}
	
	private void createWebhook(TextChannel channel, WebhookMessage message) {
		if (!channel.getGuild().getSelfMember().hasPermission(channel, Permission.MANAGE_WEBHOOKS)) {
			return;
		}

		channel.createWebhook("Sx4 - YouTube").queue(webhook -> {
			WebhookClient webhookClient = new WebhookClientBuilder(webhook.getUrl())
				.setExecutorService(this.scheduledExecutor)
				.setHttpClient(this.client)
				.build();
			
			this.webhooks.put(channel.getIdLong(), webhookClient);
			
			Bson update = Updates.combine(
				Updates.set("webhook.id", webhook.getIdLong()),
				Updates.set("webhook.token", webhook.getToken())
			);

			this.bot.getMongo().updateManyYouTubeNotifications(Filters.eq("channelId", channel.getIdLong()), update)
				.thenCompose(result -> webhookClient.send(message))
				.whenComplete((webhookMessage, exception) -> {
					if (exception instanceof HttpException && ((HttpException) exception).getCode() == 404) {
						this.webhooks.remove(channel.getIdLong());

						this.createWebhook(channel, message);
					} else {
						ExceptionUtility.sendErrorMessage(this.bot.getShardManager(), exception);
					}
				});
		});
	}

	public void onYouTubeUpload(YouTubeUploadEvent event) {
		this.notificationExecutor.submit(() -> {
			ShardManager shardManager = this.bot.getShardManager();

			String uploaderId = event.getChannel().getId();
			this.bot.getMongo().getYouTubeNotifications(Filters.eq("uploaderId", uploaderId), MongoDatabase.EMPTY_DOCUMENT).forEach(notification -> {
				long channelId = notification.getLong("channelId");

				TextChannel textChannel = shardManager.getTextChannelById(channelId);
				if (textChannel != null) {
					Document webhookData = notification.get("webhook", MongoDatabase.EMPTY_DOCUMENT);

					WebhookMessage message;
					try {
						message = this.format(event, notification.get("message", YouTubeManager.DEFAULT_MESSAGE))
							.setAvatarUrl(webhookData.get("avatar", shardManager.getShardById(0).getSelfUser().getEffectiveAvatarUrl()))
							.setUsername(webhookData.get("name", "Sx4 - YouTube"))
							.build();
					} catch (IllegalArgumentException e) {
						// possibly create an error field when this happens so the user can debug what went wrong
						this.bot.getMongo().updateYouTubeNotificationById(notification.getObjectId("_id"), Updates.unset("message")).whenComplete(MongoDatabase.exceptionally(this.bot.getShardManager()));
						return;
					}

					WebhookClient webhook;
					if (this.webhooks.containsKey(channelId)) {
						webhook = this.webhooks.get(channelId);
					} else if (!webhookData.containsKey("id")) {
						this.createWebhook(textChannel, message);

						return;
					} else {
						webhook = new WebhookClientBuilder(webhookData.getLong("id"), webhookData.getString("token"))
							.setExecutorService(this.scheduledExecutor)
							.setHttpClient(this.client)
							.build();

						this.webhooks.put(channelId, webhook);
					}

					webhook.send(message).whenComplete((webhookMessage, exception) -> {
						if (exception instanceof HttpException && ((HttpException) exception).getCode() == 404) {
							this.webhooks.remove(channelId);

							this.createWebhook(textChannel, message);
						}
					});
				} else {
					this.bot.getMongo().deleteYouTubeNotificationById(notification.getObjectId("_id")).whenComplete((result, exception) -> {
						if (ExceptionUtility.sendErrorMessage(this.bot.getShardManager(), exception)) {
							return;
						}

						this.webhooks.remove(channelId);
					});
				}
			});

			Document data = new Document("type", YouTubeType.UPLOAD.getRaw())
				.append("videoId", event.getVideo().getId())
				.append("title", event.getVideo().getTitle())
				.append("uploaderId", event.getChannel().getId());

			this.bot.getMongo().insertYouTubeNotificationLog(data).whenComplete(MongoDatabase.exceptionally(this.bot.getShardManager()));
		});
	}
	
	public void onYouTubeDelete(YouTubeDeleteEvent event) {
		this.bot.getMongo().deleteManyYouTubeNotificationLogs(event.getVideoId()).whenComplete(MongoDatabase.exceptionally(this.bot.getShardManager()));
	}
	
	public void onYouTubeUpdateTitle(YouTubeUpdateTitleEvent event) {
		Document data = new Document("type", YouTubeType.TITLE.getRaw())
			.append("videoId", event.getVideo().getId())
			.append("title", event.getVideo().getTitle())
			.append("uploaderId", event.getChannel().getId());

		this.bot.getMongo().insertYouTubeNotificationLog(data).whenComplete(MongoDatabase.exceptionally(this.bot.getShardManager()));
	}

	public void onEvent(GenericEvent event) {
		if (event instanceof TextChannelDeleteEvent) {
			long channelId = ((TextChannelDeleteEvent) event).getChannel().getIdLong();

			this.bot.getMongo().deleteManyYouTubeNotifications(Filters.eq("channelId", channelId)).whenComplete((result, exception) -> {
				if (ExceptionUtility.sendErrorMessage(this.bot.getShardManager(), exception)) {
					return;
				} 
				
				this.webhooks.remove(channelId);
			});
		}
	}
	
}
