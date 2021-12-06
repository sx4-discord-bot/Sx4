package com.sx4.bot.handlers;

import club.minnced.discord.webhook.send.WebhookMessage;
import club.minnced.discord.webhook.send.WebhookMessageBuilder;
import com.mongodb.client.model.*;
import com.sx4.bot.core.Sx4;
import com.sx4.bot.database.mongo.MongoDatabase;
import com.sx4.bot.database.mongo.model.Operators;
import com.sx4.bot.entities.youtube.YouTubeType;
import com.sx4.bot.events.youtube.YouTubeDeleteEvent;
import com.sx4.bot.events.youtube.YouTubeUpdateTitleEvent;
import com.sx4.bot.events.youtube.YouTubeUploadEvent;
import com.sx4.bot.formatter.JsonFormatter;
import com.sx4.bot.hooks.YouTubeListener;
import com.sx4.bot.managers.YouTubeManager;
import com.sx4.bot.utility.ExceptionUtility;
import com.sx4.bot.utility.MessageUtility;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.channel.text.TextChannelDeleteEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.sharding.ShardManager;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class YouTubeHandler implements YouTubeListener, EventListener {
	
	private final ExecutorService executor = Executors.newCachedThreadPool();

	private final Sx4 bot;

	public YouTubeHandler(Sx4 bot) {
		this.bot = bot;
	}
	
	private WebhookMessageBuilder format(YouTubeUploadEvent event, Document document) {
		Document formattedDocument = new JsonFormatter(document)
			.addVariable("channel", event.getChannel())
			.addVariable("video", event.getVideo())
			.parse();

		return MessageUtility.fromJson(formattedDocument);
	}

	public void onYouTubeUpload(YouTubeUploadEvent event) {
		ShardManager shardManager = this.bot.getShardManager();

		String uploaderId = event.getChannel().getId();

		List<Bson> guildPipeline = List.of(
			Aggregates.match(Operators.expr(Operators.eq("$_id", "$$guildId"))),
			Aggregates.project(Projections.computed("premium", Operators.lt(Operators.nowEpochSecond(), Operators.ifNull("$premium.endAt", 0L))))
		);

		List<Bson> pipeline = List.of(
			Aggregates.match(Filters.eq("uploaderId", uploaderId)),
			Aggregates.lookup("guilds", List.of(new Variable<>("guildId", "$guildId")), guildPipeline, "premium"),
			Aggregates.addFields(new Field<>("premium", Operators.cond(Operators.isEmpty("$premium"), false, Operators.get(Operators.arrayElemAt("$premium", 0), "premium"))))
		);

		this.bot.getMongo().aggregateYouTubeNotifications(pipeline).whenComplete((notifications, aggregateException) -> {
			if (ExceptionUtility.sendErrorMessage(aggregateException)) {
				return;
			}

			this.executor.submit(() -> {
				List<WriteModel<Document>> bulkUpdate = new ArrayList<>();
				notifications.forEach(notification -> {
					long channelId = notification.getLong("channelId");

					TextChannel textChannel = shardManager.getTextChannelById(channelId);
					if (textChannel == null) {
						return;
					}

					Document webhookData = notification.get("webhook", MongoDatabase.EMPTY_DOCUMENT);
					boolean premium = notification.getBoolean("premium");

					WebhookMessage message;
					try {
						message = this.format(event, notification.get("message", YouTubeManager.DEFAULT_MESSAGE))
							.setAvatarUrl(premium ? webhookData.get("avatar", this.bot.getConfig().getYouTubeAvatar()) : this.bot.getConfig().getYouTubeAvatar())
							.setUsername(premium ? webhookData.get("name", "Sx4 - YouTube") : "Sx4 - YouTube")
							.build();
					} catch (IllegalArgumentException e) {
						// possibly create an error field when this happens so the user can debug what went wrong
						bulkUpdate.add(new UpdateOneModel<>(Filters.eq("_id", notification.getObjectId("_id")), Updates.unset("message"), new UpdateOptions()));
						return;
					}

					this.bot.getYouTubeManager().sendYouTubeNotification(textChannel, webhookData, message).whenComplete(MongoDatabase.exceptionally());
				});

				if (!bulkUpdate.isEmpty()) {
					this.bot.getMongo().bulkWriteYouTubeNotifications(bulkUpdate).whenComplete(MongoDatabase.exceptionally());
				}

				Document data = new Document("type", YouTubeType.UPLOAD.getRaw())
					.append("videoId", event.getVideo().getId())
					.append("title", event.getVideo().getTitle())
					.append("uploaderId", event.getChannel().getId());

				this.bot.getMongo().insertYouTubeNotificationLog(data).whenComplete(MongoDatabase.exceptionally());
			});
		});
	}
	
	public void onYouTubeDelete(YouTubeDeleteEvent event) {
		this.bot.getMongo().deleteManyYouTubeNotificationLogs(event.getVideoId()).whenComplete(MongoDatabase.exceptionally());
	}
	
	public void onYouTubeUpdateTitle(YouTubeUpdateTitleEvent event) {
		Document data = new Document("type", YouTubeType.TITLE.getRaw())
			.append("videoId", event.getVideo().getId())
			.append("title", event.getVideo().getTitle())
			.append("uploaderId", event.getChannel().getId());

		this.bot.getMongo().insertYouTubeNotificationLog(data).whenComplete(MongoDatabase.exceptionally());
	}

	public void onEvent(GenericEvent event) {
		if (event instanceof TextChannelDeleteEvent) {
			long channelId = ((TextChannelDeleteEvent) event).getChannel().getIdLong();

			this.bot.getMongo().deleteManyYouTubeNotifications(Filters.eq("channelId", channelId)).whenComplete((result, exception) -> {
				if (ExceptionUtility.sendErrorMessage(exception)) {
					return;
				} 
				
				this.bot.getYouTubeManager().removeWebhook(channelId);
			});
		}
	}
	
}
