package com.sx4.bot.managers;

import com.mongodb.client.model.*;
import com.sx4.bot.database.Database;
import org.bson.Document;

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

	private static final PremiumManager INSTANCE = new PremiumManager();

	public static PremiumManager get() {
		return PremiumManager.INSTANCE;
	}

	private final Map<Long, ScheduledFuture<?>> executors;
	private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

	private PremiumManager() {
		this.executors = new HashMap<>();
	}

	public ScheduledExecutorService getExecutor() {
		return this.executor;
	}

	public ScheduledFuture<?> getExecutor(long guildId) {
		return this.executors.get(guildId);
	}

	public void deleteExecutor(long guildId) {
		ScheduledFuture<?> executor = this.executors.remove(guildId);
		if (executor != null && !executor.isDone()) {
			executor.cancel(true);
		}
	}

	public void putExecutor(long guildId, ScheduledFuture<?> executor, long seconds) {
		ScheduledFuture<?> oldExecutor = this.executors.get(guildId);
		if (oldExecutor != null && !oldExecutor.isDone()) {
			seconds += oldExecutor.getDelay(TimeUnit.SECONDS);
			oldExecutor.cancel(true);
		}

		this.executors.put(guildId, executor);
	}

	public void schedulePremiumExpiry(long guildId, long seconds) {
		this.putExecutor(guildId, this.executor.schedule(() -> this.endPremium(guildId), seconds, TimeUnit.SECONDS), seconds);
	}

	public void endPremium(long guildId) {
		UpdateOneModel<Document> model = this.endPremiumBulk(guildId);
		if (model != null) {
			Database.get().updateGuild(model).whenComplete(Database.exceptionally());
		}
	}

	public UpdateOneModel<Document> endPremiumBulk(long guildId) {
		// remove premium features

		return new UpdateOneModel<>(Filters.eq("_id", guildId), Updates.unset("premium"));
	}

	public void ensurePremiumExpiry() {
		Database database = Database.get();

		List<WriteModel<Document>> bulkData = new ArrayList<>();

		database.getGuilds(Filters.exists("premium.endAt"), Projections.include("premium.endAt")).forEach(data -> {
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
			database.bulkWriteGuilds(bulkData).whenComplete(Database.exceptionally());
		}
	}

}
