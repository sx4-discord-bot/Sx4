package com.sx4.bot.handlers;

import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.bson.Document;
import org.bson.conversions.Bson;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.sx4.bot.core.Sx4Bot;
import com.sx4.bot.database.Database;
import com.sx4.bot.entities.youtube.YouTubeChannel;
import com.sx4.bot.entities.youtube.YouTubeType;
import com.sx4.bot.entities.youtube.YouTubeVideo;
import com.sx4.bot.events.youtube.YouTubeDeleteEvent;
import com.sx4.bot.events.youtube.YouTubeUpdateTitleEvent;
import com.sx4.bot.events.youtube.YouTubeUploadEvent;
import com.sx4.bot.hooks.YouTubeListener;
import com.sx4.bot.managers.YouTubeManager;
import com.sx4.bot.utility.ExceptionUtility;

import club.minnced.discord.webhook.WebhookClient;
import club.minnced.discord.webhook.WebhookClientBuilder;
import club.minnced.discord.webhook.exception.HttpException;
import club.minnced.discord.webhook.send.WebhookMessage;
import club.minnced.discord.webhook.send.WebhookMessageBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.channel.text.TextChannelDeleteEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.sharding.ShardManager;
import okhttp3.OkHttpClient;

public class YouTubeHandler implements YouTubeListener, EventListener {
	
	private static final YouTubeHandler INSTANCE = new YouTubeHandler();
	
	public static YouTubeHandler get() {
		return YouTubeHandler.INSTANCE;
	}
	
	private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd LLLL yyyy HH:mm");
	
	private final OkHttpClient client = new OkHttpClient.Builder().build();
	
	private final ScheduledExecutorService scheduledExectuor = Executors.newSingleThreadScheduledExecutor();
	
	private final ExecutorService notificationExecutor = Executors.newCachedThreadPool();
	
	private final Map<Long, WebhookClient> webhooks = new HashMap<>();
	
	private String getMessage(YouTubeUploadEvent event, String message) {
		YouTubeChannel channel = event.getChannel();
		YouTubeVideo video = event.getVideo();
		
		int index = -1;
		while ((index = message.indexOf('{', index + 1)) != -1) {
		    if (index > 0 && message.charAt(index - 1) == '\\') {
		        message = message.substring(0, index - 1) + message.substring(index);
		        continue;
		    }

		    int endIndex = message.indexOf('}', index + 1);
		    if (endIndex != -1)  {
		        if (message.charAt(endIndex - 1) == '\\') {
		            message = message.substring(0, endIndex - 1) + message.substring(endIndex);
		            continue;
		        } else {
		            String formatter = message.substring(index + 1, endIndex);
		            switch (formatter.trim().toLowerCase()) {
		            	case "channel.name":
		            		message = message.substring(0, index) + channel.getName() + message.substring(endIndex + 1);
		            		break;
		            	case "channel.url":
		            		message = message.substring(0, index) + channel.getUrl() + message.substring(endIndex + 1);
		            		break;
		            	case "channel.id":
		            		message = message.substring(0, index) + channel.getId() + message.substring(endIndex + 1);
		            		break;
		            	case "video.title":
		            		message = message.substring(0, index) + video.getTitle() + message.substring(endIndex + 1);
		            		break;
		            	case "video.url":
		            		message = message.substring(0, index) + video.getUrl() + message.substring(endIndex + 1);
		            		break;
		            	case "video.id":
		            		message = message.substring(0, index) + video.getId() + message.substring(endIndex + 1);
		            		break;
		            	case "video.published":
		            		message = message.substring(0, index) + video.getPublishedAt().format(this.formatter) + message.substring(endIndex + 1);
		            		break;
		            }
		        }
		    }
		}
		
		return message;
	}
	
	public void ensureWebhooks() {
		Bson projection = Projections.include("youtube.notifications.channelId", "youtube.notifications.webhookId", "youtube.notifications.webhookToken");
		Database.get().getGuilds(Filters.elemMatch("youtube.notifications", Filters.exists("webhookId")), projection).forEach((Document guildData) -> {
			List<Document> notifications = guildData.getEmbedded(List.of("youtube", "notification"), Collections.emptyList());
			for (Document data : notifications) {
				long webhookId = data.get("webhookId", 0L);
				String webhookToken = data.getString("webhookToken");
				if (webhookId != 0L && webhookToken != null) {
					WebhookClient webhook = new WebhookClientBuilder(webhookId, webhookToken)
							.setExecutorService(this.scheduledExectuor)
							.setHttpClient(this.client)
							.build();
					
					this.webhooks.putIfAbsent(data.getLong("channelId"), webhook);
				}
			}
		});
	}
	
	private void createNewWebhook(YouTubeUploadEvent event, TextChannel textChannel, WebhookMessage message) {
		textChannel.createWebhook("Sx4 - YouTube").queue(newWebhook -> {
			WebhookClient webhookClient = new WebhookClientBuilder(newWebhook.getUrl())
					.setExecutorService(this.scheduledExectuor)
					.setHttpClient(this.client)
					.build();
			
			this.webhooks.put(textChannel.getIdLong(), webhookClient);
			
			Bson update = Updates.combine(Updates.set("youtube.notifications.$[notification].webhookId", webhookClient.getId()), Updates.set("youtube.notifications.$[notification].webhookToken", newWebhook.getToken()));
			UpdateOptions options = new UpdateOptions().arrayFilters(List.of(Filters.eq("notification.channelId", textChannel.getIdLong())));
			
			Database.get().updateGuildById(textChannel.getGuild().getIdLong(), update, options)
				.thenCompose(result -> webhookClient.send(message))
				.whenComplete((webhookMessage, exception) -> {
					if (exception instanceof HttpException && ((HttpException) exception).getCode() == 404) {
						this.webhooks.remove(textChannel.getIdLong());
						
						if (textChannel.getGuild().getSelfMember().hasPermission(textChannel, Permission.MANAGE_WEBHOOKS)) {
							this.createNewWebhook(event, textChannel, message);
						}
					} else {
						ExceptionUtility.sendErrorMessage(exception);
					}
				});
		});
	}

	public void onYouTubeUpload(YouTubeUploadEvent event) {
		this.notificationExecutor.submit(() -> {
			ShardManager shardManager = Sx4Bot.getShardManager();
			
			try {
				Database database = Database.get();

				String channelId = event.getChannel().getId();
				database.getGuilds(Filters.elemMatch("youtube.notifications", Filters.eq("uploaderId", channelId)), Projections.include("youtube.notifications")).forEach((Document guildData) -> {
					Guild guild = shardManager.getGuildById(guildData.getLong("_id"));
					
					if (guild != null) {
						List<Document> notifications = guildData.getEmbedded(List.of("youtube", "notifications"), Collections.emptyList());
						for (Document notification : notifications) {
							if (notification.getString("uploaderId").equals(channelId)) {
								long textChannelId = notification.getLong("channelId");
								TextChannel textChannel = guild.getTextChannelById(textChannelId);
								if (textChannel != null) {
									String messageContent = this.getMessage(event, notification.get("message", YouTubeManager.DEFAULT_MESSAGE));
									
									WebhookMessage message = new WebhookMessageBuilder()
										.setAvatarUrl(notification.get("avatar", Sx4Bot.getShardManager().getShardById(0).getSelfUser().getEffectiveAvatarUrl()))
										.setUsername(notification.get("name", "Sx4 - YouTube"))
										.setContent(messageContent)
										.build();
									
									long webhookId = notification.get("webhookId", 0L);
									String webhookToken = notification.getString("webhookToken");
									
									WebhookClient webhook;
									if (this.webhooks.containsKey(textChannel.getIdLong())) {
										webhook = this.webhooks.get(textChannel.getIdLong());
									} else {
										if (webhookId == 0L || webhookToken == null) {
											if (textChannel.getGuild().getSelfMember().hasPermission(textChannel, Permission.MANAGE_WEBHOOKS)) {
												this.createNewWebhook(event, textChannel, message);
											}
											
											return;
										} else {
											webhook = new WebhookClientBuilder(webhookId, webhookToken)
													.setExecutorService(this.scheduledExectuor)
													.setHttpClient(this.client)
													.build();
											
											this.webhooks.put(textChannel.getIdLong(), webhook);
										}
									}
									
									webhook.send(message).whenComplete((webhookMessage, exception) -> {
										if (exception instanceof HttpException && ((HttpException) exception).getCode() == 404) {
											this.webhooks.remove(textChannel.getIdLong());
											
											if (textChannel.getGuild().getSelfMember().hasPermission(textChannel, Permission.MANAGE_WEBHOOKS)) {
												this.createNewWebhook(event, textChannel, message);
											}
										}
									});
								} else {
									database.updateGuildById(guild.getIdLong(), Updates.pull("youtube.notifications", Filters.eq("channelId", textChannelId))).whenComplete((result, exception) -> {
										if (exception != null) {
											exception.printStackTrace();
											ExceptionUtility.sendErrorMessage(exception);
										} else {
											this.webhooks.remove(textChannelId);
										}
									});
								}
							}
						}
					}
				});
				
				Document databaseData = new Document("type", YouTubeType.UPLOAD.getRaw())
						.append("videoId", event.getVideo().getId())
						.append("title", event.getVideo().getTitle())
						.append("uploaderId", event.getChannel().getId());
				
				Database.get().insertNotification(databaseData).whenComplete((result, databaseException) -> {
					if (databaseException != null) {
						databaseException.printStackTrace();
						ExceptionUtility.sendErrorMessage(databaseException);
					}
				});
			} catch (Throwable e) {
				e.printStackTrace();
				ExceptionUtility.sendErrorMessage(e);
			}
		});
	}
	
	public void onYouTubeDelete(YouTubeDeleteEvent event) {
		Database.get().deleteManyNotifications(event.getVideoId()).whenComplete((result, exception) -> {
			if (exception != null) {
				exception.printStackTrace();
				ExceptionUtility.sendErrorMessage(exception);
			}
		});
	}
	
	public void onYouTubeUpdateTitle(YouTubeUpdateTitleEvent event) {
		Document data = new Document("type", YouTubeType.TITLE.getRaw())
				.append("videoId", event.getVideo().getId())
				.append("title", event.getVideo().getTitle())
				.append("uploaderId", event.getChannel().getId());
		
		Database.get().insertNotification(data).whenComplete((result, exception) -> {
			if (exception != null) {
				exception.printStackTrace();
				ExceptionUtility.sendErrorMessage(exception);
			}
		});
	}

	public void onEvent(GenericEvent event) {
		if (event instanceof TextChannelDeleteEvent) {
			TextChannelDeleteEvent deleteEvent = (TextChannelDeleteEvent) event;
			
			Database.get().updateGuildById(deleteEvent.getGuild().getIdLong(), Updates.pull("youtube.notifications", Filters.eq("channelId", deleteEvent.getChannel().getIdLong()))).whenComplete((result, exception) -> {
				if (exception != null) {
					exception.printStackTrace();
					ExceptionUtility.sendErrorMessage(exception);
				} else {
					this.webhooks.remove(deleteEvent.getChannel().getIdLong());
				}
			});
		}
	}
	
}
