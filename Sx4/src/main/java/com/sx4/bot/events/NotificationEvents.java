package com.sx4.bot.events;

import java.time.format.DateTimeFormatter;
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
import com.sx4.bot.core.Sx4CommandEventListener;
import com.sx4.bot.database.Database;
import com.sx4.bot.settings.Settings;
import com.sx4.bot.youtube.YouTubeChannel;
import com.sx4.bot.youtube.YouTubeEvent;
import com.sx4.bot.youtube.YouTubeListener;
import com.sx4.bot.youtube.YouTubeVideo;

import club.minnced.discord.webhook.WebhookClient;
import club.minnced.discord.webhook.WebhookClientBuilder;
import club.minnced.discord.webhook.exception.HttpException;
import club.minnced.discord.webhook.send.WebhookMessage;
import club.minnced.discord.webhook.send.WebhookMessageBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.sharding.ShardManager;
import okhttp3.OkHttpClient;

public class NotificationEvents implements YouTubeListener {
	
	private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd LLLL yyyy HH:mm");
	
	private final String defaultMessage = "**[{channel.name}]({channel.url})** just uploaded a new video!\n{video.url}";
	
	private final OkHttpClient client = new OkHttpClient.Builder().build();
	
	private final ScheduledExecutorService scheduledExectuor = Executors.newSingleThreadScheduledExecutor();
	
	private final ExecutorService notificationExecutor = Executors.newCachedThreadPool();
	
	private final Map<Long, WebhookClient> webhooks = new HashMap<>();
	
	private String getMessage(YouTubeEvent event, String message) {
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
		            		message = message.substring(0, index) + video.getTimePublishedAt().format(this.formatter) + message.substring(endIndex + 1);
		            		break;
		            }
		        }
		    }
		}
		
		return message;
	}
	
	private void createNewWebhook(TextChannel textChannel, String messageContent) {
		textChannel.createWebhook("Sx4 - YouTube").queue(newWebhook -> {
			WebhookClient webhookClient = new WebhookClientBuilder(newWebhook.getUrl())
					.setExecutorService(this.scheduledExectuor)
					.setHttpClient(this.client)
					.build();
			
			this.webhooks.put(textChannel.getIdLong(), webhookClient);
			
			Bson update = Updates.combine(Updates.set("youtubeNotifications.$[].webhookId", webhookClient.getId()), Updates.set("youtubeNotifications.$[].webhookToken", newWebhook.getToken()));
			Database.get().updateGuildById(textChannel.getGuild().getIdLong(), Filters.eq("youtubeNotifications.channelId", textChannel.getIdLong()), update, new UpdateOptions(), (result, databaseException) -> {
				if (databaseException != null) {
					databaseException.printStackTrace();
				} else {
					WebhookMessage message = new WebhookMessageBuilder()
							.setAvatarUrl(Sx4Bot.getShardManager().getShardById(0).getSelfUser().getEffectiveAvatarUrl())
							.setContent(messageContent)
							.build();
					
					webhookClient.send(message);
				}
			});
		});
	}

	public void onVideoUpload(YouTubeEvent event) {
		this.notificationExecutor.submit(() -> {
			ShardManager shardManager = Sx4Bot.getShardManager();
			
			try {
				Database database = Database.get();

				Bson channelFilter = Filters.eq("youtubeNotifications.uploaderId", event.getChannel().getId());
				database.getGuilds(channelFilter, Projections.include("youtubeNotifications")).forEach((Document guildData) -> {
					List<Document> notifications = guildData.getList("youtubeNotifications", Document.class);
					for (Document data : notifications) {
						TextChannel textChannel = shardManager.getTextChannelById(data.getLong("channelId"));
						if (textChannel != null) {
							String messageContent = this.getMessage(event, data.get("message", this.defaultMessage));
							
							WebhookClient webhook;
							
							Long webhookId = data.getLong("webhookId");
							String webhookToken = data.getString("webhookToken");
							if (this.webhooks.containsKey(textChannel.getIdLong())) {
								webhook = this.webhooks.get(textChannel.getIdLong());
							} else {
								if (webhookId == null || webhookToken == null) {
									if (textChannel.getGuild().getSelfMember().hasPermission(Permission.MANAGE_WEBHOOKS)) {
										this.createNewWebhook(textChannel, messageContent);
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
							
							WebhookMessage message = new WebhookMessageBuilder()
									.setAvatarUrl(shardManager.getShardById(0).getSelfUser().getEffectiveAvatarUrl())
									.setContent(messageContent)
									.build();
							
							webhook.send(message).whenCompleteAsync((webhookMessage, exception) -> {
								if (exception != null) {
									if (exception instanceof HttpException) {
										/* Ugly catch, blame JDA */
										if (exception.getMessage().startsWith("Request returned failure 404")) {
											this.webhooks.remove(textChannel.getIdLong());
											
											if (textChannel.getGuild().getSelfMember().hasPermission(Permission.MANAGE_WEBHOOKS)) {
												this.createNewWebhook(textChannel, messageContent);
											}
											
											return;
										}
									}
								}
							});
						}
					}
				});
			} catch (Throwable e) {
				e.printStackTrace();
				Sx4CommandEventListener.sendErrorMessage(shardManager.getTextChannelById(Settings.ERRORS_CHANNEL_ID), e, new Object[0]);
			}
		});
	}

	public void onVideoDelete(YouTubeEvent event) {
		
	}
	
}
