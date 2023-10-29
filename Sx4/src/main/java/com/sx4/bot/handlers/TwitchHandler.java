package com.sx4.bot.handlers;

import com.mongodb.client.model.*;
import com.sx4.bot.core.Sx4;
import com.sx4.bot.database.mongo.MongoDatabase;
import com.sx4.bot.database.mongo.model.Operators;
import com.sx4.bot.entities.twitch.TwitchStreamType;
import com.sx4.bot.entities.webhook.WebhookChannel;
import com.sx4.bot.events.twitch.TwitchStreamStartEvent;
import com.sx4.bot.formatter.output.JsonFormatter;
import com.sx4.bot.hooks.TwitchListener;
import com.sx4.bot.managers.TwitchManager;
import com.sx4.bot.utility.ExceptionUtility;
import com.sx4.bot.utility.MessageUtility;
import net.dv8tion.jda.api.entities.channel.unions.GuildMessageChannelUnion;
import net.dv8tion.jda.api.sharding.ShardManager;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TwitchHandler implements TwitchListener {

	private final Sx4 bot;

	private final ExecutorService executor = Executors.newSingleThreadExecutor();

	public TwitchHandler(Sx4 bot) {
		this.bot = bot;
	}

	private MessageCreateBuilder format(TwitchStreamStartEvent event, Document document) {
		Document formattedDocument = new JsonFormatter(document)
			.addVariable("streamer", event.getStreamer())
			.addVariable("stream", event.getStream())
			.parse();

		return MessageUtility.fromCreateJson(formattedDocument, true);
	}

	@Override
	public void onStreamStart(TwitchStreamStartEvent event) {
		ShardManager shardManager = this.bot.getShardManager();

		String streamerId = event.getStreamer().getId();

		List<Bson> guildPipeline = List.of(
			Aggregates.match(Operators.expr(Operators.eq("$_id", "$$guildId"))),
			Aggregates.project(Projections.computed("premium", Operators.lt(Operators.nowEpochSecond(), Operators.ifNull("$premium.endAt", 0L))))
		);

		List<Bson> pipeline = List.of(
			Aggregates.match(Filters.eq("streamerId", streamerId)),
			Aggregates.lookup("guilds", List.of(new Variable<>("guildId", "$guildId")), guildPipeline, "premium"),
			Aggregates.addFields(new Field<>("premium", Operators.cond(Operators.isEmpty("$premium"), false, Operators.get(Operators.arrayElemAt("$premium", 0), "premium"))))
		);

		this.bot.getMongo().aggregateTwitchNotifications(pipeline).whenComplete((notifications, aggregateException) -> {
			if (ExceptionUtility.sendErrorMessage(aggregateException)) {
				return;
			}

			long type = event.getStream().getType().getRaw();

			this.executor.submit(() -> {
				List<WriteModel<Document>> bulkUpdate = new ArrayList<>();
				notifications.forEach(notification -> {
					if (!notification.getBoolean("enabled", true)) {
						return;
					}

					long types = notification.get("types", TwitchStreamType.ALL);
					if ((types & type) != type) {
						return;
					}

					long channelId = notification.getLong("channelId");

					GuildMessageChannelUnion channel = shardManager.getChannelById(GuildMessageChannelUnion.class, channelId);
					if (channel == null) {
						return;
					}

					Document webhookData = notification.get("webhook", MongoDatabase.EMPTY_DOCUMENT);
					boolean premium = notification.getBoolean("premium");

					MessageCreateData message;
					try {
						message = this.format(event, notification.get("message", TwitchManager.DEFAULT_MESSAGE))
							.build();
					} catch (IllegalArgumentException e) {
						bulkUpdate.add(new UpdateOneModel<>(Filters.eq("_id", notification.getObjectId("_id")), Updates.unset("message"), new UpdateOptions()));
						return;
					}

					this.bot.getTwitchManager().sendTwitchNotification(new WebhookChannel(channel), webhookData, message, premium).whenComplete(MongoDatabase.exceptionally());
				});

				if (!bulkUpdate.isEmpty()) {
					this.bot.getMongo().bulkWriteTwitchNotifications(bulkUpdate).whenComplete(MongoDatabase.exceptionally());
				}
			});
		});
	}

}
