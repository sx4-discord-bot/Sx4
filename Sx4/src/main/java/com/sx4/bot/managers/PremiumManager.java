package com.sx4.bot.managers;

import com.mongodb.client.model.*;
import com.sx4.bot.core.Sx4;
import com.sx4.bot.database.mongo.MongoDatabase;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class PremiumManager {

	private final Map<Long, ScheduledFuture<?>> executors;
	private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

	private final Sx4 bot;

	public PremiumManager(Sx4 bot) {
		this.executors = new HashMap<>();
		this.bot = bot;
	}

	public ScheduledExecutorService getExecutor() {
		return this.executor;
	}

	public void schedulePremiumExpiry(long guildId, long seconds) {
		ScheduledFuture<?> oldExecutor = this.executors.get(guildId);
		if (oldExecutor != null && !oldExecutor.isDone()) {
			oldExecutor.cancel(true);
		}

		this.executors.put(guildId, this.executor.schedule(() -> this.endPremium(guildId), seconds, TimeUnit.SECONDS));
	}

	public void endPremium(long guildId) {
		this.bot.getMongo().updateGuild(this.endPremiumBulk(guildId)).whenComplete(MongoDatabase.exceptionally(this.bot.getShardManager()));
	}

	public UpdateOneModel<Document> endPremiumBulk(long guildId) {
		// Disable anti regex until only 3 are active
		// Disable loggers until only 3 are active
		// Unset YouTube notifications webhook name and avatar
		// Unset Loggers webhook name and avatar

		Bson guildUpdate = Updates.combine(
			Updates.unset("premium"),
			Updates.unset("welcomer.webhook.name"),
			Updates.unset("welcomer.webhook.avatar"),
			Updates.unset("leaver.webhook.name"),
			Updates.unset("leaver.webhook.avatar"),
			Updates.unset("starboard.webhook.name"),
			Updates.unset("starboard.webhook.avatar"),
			Updates.unset("modLog.webhook.name"),
			Updates.unset("modLog.webhook.avatar"),
			Updates.unset("suggestion.webhook.name"),
			Updates.unset("suggestion.webhook.avatar")
		);

		return new UpdateOneModel<>(Filters.eq("_id", guildId), guildUpdate);
	}

	public void ensurePremiumExpiry() {
		List<WriteModel<Document>> bulkData = new ArrayList<>();

		this.bot.getMongo().getGuilds(Filters.exists("premium.endAt"), Projections.include("premium.endAt")).forEach(data -> {
			long endAt = data.getEmbedded(List.of("premium", "endAt"), 0L), timeNow = Clock.systemUTC().instant().getEpochSecond();
			if (endAt != 0) {
				if (endAt - timeNow > 0) {
					this.schedulePremiumExpiry(data.getLong("_id"), endAt - timeNow);
				} else {
					bulkData.add(this.endPremiumBulk(data.getLong("_id")));
				}
			}
		});

		if (!bulkData.isEmpty()) {
			this.bot.getMongo().bulkWriteGuilds(bulkData).whenComplete(MongoDatabase.exceptionally(this.bot.getShardManager()));
		}
	}

}
