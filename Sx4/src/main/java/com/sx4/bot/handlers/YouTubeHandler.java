package com.sx4.bot.handlers;

import club.minnced.discord.webhook.WebhookClient;
import club.minnced.discord.webhook.WebhookClientBuilder;
import club.minnced.discord.webhook.exception.HttpException;
import club.minnced.discord.webhook.send.WebhookMessage;
import club.minnced.discord.webhook.send.WebhookMessageBuilder;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.sx4.bot.core.Sx4;
import com.sx4.bot.database.Database;
import com.sx4.bot.entities.youtube.YouTubeChannel;
import com.sx4.bot.entities.youtube.YouTubeType;
import com.sx4.bot.entities.youtube.YouTubeVideo;
import com.sx4.bot.events.youtube.YouTubeDeleteEvent;
import com.sx4.bot.events.youtube.YouTubeUpdateTitleEvent;
import com.sx4.bot.events.youtube.YouTubeUploadEvent;
import com.sx4.bot.formatter.Formatter;
import com.sx4.bot.hooks.YouTubeListener;
import com.sx4.bot.managers.YouTubeManager;
import com.sx4.bot.utility.ExceptionUtility;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.channel.text.TextChannelDeleteEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.sharding.ShardManager;
import okhttp3.OkHttpClient;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class YouTubeHandler implements YouTubeListener, EventListener {
	
	private static final YouTubeHandler INSTANCE = new YouTubeHandler();
	
	public static YouTubeHandler get() {
		return YouTubeHandler.INSTANCE;
	}
	
	private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd LLLL yyyy HH:mm");
	
	private final OkHttpClient client = new OkHttpClient();
	
	private final ScheduledExecutorService scheduledExectuor = Executors.newSingleThreadScheduledExecutor();
	
	private final ExecutorService notificationExecutor = Executors.newCachedThreadPool();
	
	private final Map<Long, WebhookClient> webhooks;

	private YouTubeHandler() {
		this.webhooks = new HashMap<>();
	}
	
	private String format(YouTubeUploadEvent event, String message) {
		YouTubeChannel channel = event.getChannel();
		YouTubeVideo video = event.getVideo();

		return new Formatter(message)
			.append("channel.name", channel.getName())
			.append("channel.url", channel.getUrl())
			.append("channel.id", channel.getId())
			.append("video.title", video.getTitle())
			.append("video.url", video.getUrl())
			.append("video.id", video.getId())
			.append("video.thumbnail", video.getThumbnail())
			.append("video.published", video.getPublishedAt().format(this.formatter))
			.parse();
	}
	
	private void createWebhook(TextChannel textChannel, WebhookMessage message) {
		textChannel.createWebhook("Sx4 - YouTube").queue(webhook -> {
			WebhookClient webhookClient = new WebhookClientBuilder(webhook.getUrl())
				.setExecutorService(this.scheduledExectuor)
				.setHttpClient(this.client)
				.build();
			
			this.webhooks.put(textChannel.getIdLong(), webhookClient);
			
			Bson update = Updates.combine(
				Updates.set("youtube.notifications.$[notification].webhook.id", webhook.getIdLong()),
				Updates.set("youtube.notifications.$[notification].webhook.token", webhook.getToken())
			);

			UpdateOptions options = new UpdateOptions().arrayFilters(List.of(Filters.eq("notification.channelId", textChannel.getIdLong())));
			Database.get().updateGuildById(textChannel.getGuild().getIdLong(), update, options)
				.thenCompose(result -> webhookClient.send(message))
				.whenComplete((webhookMessage, exception) -> {
					if (exception instanceof HttpException && ((HttpException) exception).getCode() == 404) {
						this.webhooks.remove(textChannel.getIdLong());
						
						if (textChannel.getGuild().getSelfMember().hasPermission(textChannel, Permission.MANAGE_WEBHOOKS)) {
							this.createWebhook(textChannel, message);
						}
					} else {
						ExceptionUtility.sendErrorMessage(exception);
					}
				});
		});
	}

	public void onYouTubeUpload(YouTubeUploadEvent event) {
		this.notificationExecutor.submit(() -> {
			ShardManager shardManager = Sx4.get().getShardManager();
			
			try {
				Database database = Database.get();

				String channelId = event.getChannel().getId();
				database.getGuilds(Filters.elemMatch("youtube.notifications", Filters.eq("uploaderId", channelId)), Projections.include("youtube.notifications")).forEach(guildData -> {
					Guild guild = shardManager.getGuildById(guildData.getLong("_id"));
					if (guild == null) {
						return;
					}

					List<Document> notifications = guildData.getEmbedded(List.of("youtube", "notifications"), Collections.emptyList());
					for (Document notification : notifications) {
						if (notification.getString("uploaderId").equals(channelId)) {
							long textChannelId = notification.getLong("channelId");
							TextChannel textChannel = guild.getTextChannelById(textChannelId);
							if (textChannel != null) {
								String messageContent = this.format(event, notification.get("message", YouTubeManager.DEFAULT_MESSAGE));

								Document webhookData = notification.get("webhook", Database.EMPTY_DOCUMENT);

								WebhookMessage message = new WebhookMessageBuilder()
									.setAvatarUrl(webhookData.get("avatar", shardManager.getShardById(0).getSelfUser().getEffectiveAvatarUrl()))
									.setUsername(webhookData.get("name", "Sx4 - YouTube"))
									.setContent(messageContent)
									.build();

								WebhookClient webhook;
								if (this.webhooks.containsKey(textChannel.getIdLong())) {
									webhook = this.webhooks.get(textChannel.getIdLong());
								} else if (!webhookData.containsKey("id")) {
									if (textChannel.getGuild().getSelfMember().hasPermission(textChannel, Permission.MANAGE_WEBHOOKS)) {
										this.createWebhook(textChannel, message);
									}

									return;
								} else {
									webhook = new WebhookClientBuilder(webhookData.getLong("id"), webhookData.getString("token"))
										.setExecutorService(this.scheduledExectuor)
										.setHttpClient(this.client)
										.build();

									this.webhooks.put(textChannel.getIdLong(), webhook);
								}

								webhook.send(message).whenComplete((webhookMessage, exception) -> {
									if (exception instanceof HttpException && ((HttpException) exception).getCode() == 404) {
										this.webhooks.remove(textChannel.getIdLong());

										if (textChannel.getGuild().getSelfMember().hasPermission(textChannel, Permission.MANAGE_WEBHOOKS)) {
											this.createWebhook(textChannel, message);
										}
									}
								});
							} else {
								database.updateGuildById(guild.getIdLong(), Updates.pull("youtube.notifications", Filters.eq("channelId", textChannelId))).whenComplete((result, exception) -> {
									if (ExceptionUtility.sendErrorMessage(exception)) {
										return;
									}

									this.webhooks.remove(textChannelId);
								});
							}
						}
					}
				});
				
				Document databaseData = new Document("type", YouTubeType.UPLOAD.getRaw())
					.append("videoId", event.getVideo().getId())
					.append("title", event.getVideo().getTitle())
					.append("uploaderId", event.getChannel().getId());
				
				Database.get().insertNotification(databaseData).whenComplete(Database.exceptionally());
			} catch (Throwable e) {
				e.printStackTrace();
				ExceptionUtility.sendErrorMessage(e);
			}
		});
	}
	
	public void onYouTubeDelete(YouTubeDeleteEvent event) {
		Database.get().deleteManyNotifications(event.getVideoId()).whenComplete(Database.exceptionally());
	}
	
	public void onYouTubeUpdateTitle(YouTubeUpdateTitleEvent event) {
		Document data = new Document("type", YouTubeType.TITLE.getRaw())
			.append("videoId", event.getVideo().getId())
			.append("title", event.getVideo().getTitle())
			.append("uploaderId", event.getChannel().getId());
		
		Database.get().insertNotification(data).whenComplete(Database.exceptionally());
	}

	public void onEvent(GenericEvent event) {
		if (event instanceof TextChannelDeleteEvent) {
			TextChannelDeleteEvent deleteEvent = (TextChannelDeleteEvent) event;
			
			Database.get().updateGuildById(deleteEvent.getGuild().getIdLong(), Updates.pull("youtube.notifications", Filters.eq("channelId", deleteEvent.getChannel().getIdLong()))).whenComplete((result, exception) -> {
				if (ExceptionUtility.sendErrorMessage(exception)) {
					return;
				} 
				
				this.webhooks.remove(deleteEvent.getChannel().getIdLong());
			});
		}
	}
	
}
