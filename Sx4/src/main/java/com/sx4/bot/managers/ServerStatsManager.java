package com.sx4.bot.managers;

import com.sx4.bot.core.Sx4;
import com.sx4.bot.database.Database;
import com.sx4.bot.entities.info.ServerStatsType;
import org.bson.Document;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ServerStatsManager {

	private final Map<Long, Map<ServerStatsType, Integer>> counter;
	private LocalDateTime lastUpdate;

	private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

	private final Sx4 bot;

	public ServerStatsManager(Sx4 bot) {
		this.counter = new HashMap<>();
		this.bot = bot;
		this.initialize();
	}

	public void initialize() {
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		OffsetDateTime nextHour = now.plusHours(1).withSecond(0).withMinute(0).withNano(0);

		this.executor.scheduleAtFixedRate(() -> {
			List<Document> data = this.toData(this.lastUpdate = LocalDateTime.now(ZoneOffset.UTC));
			this.clear();

			this.bot.getDatabase().insertManyServerStats(data).whenComplete(Database.exceptionally(this.bot.getShardManager()));
		}, Duration.between(now, nextHour).toSeconds(), 3600, TimeUnit.SECONDS);
	}

	public LocalDateTime getLastUpdate() {
		return this.lastUpdate;
	}

	public synchronized int getCounter(long guildId, ServerStatsType type) {
		Map<ServerStatsType, Integer> guild = this.counter.get(guildId);
		if (guild != null) {
			return guild.getOrDefault(type, 0);
		}

		return 0;
	}

	public synchronized void addCounter(long guildId, ServerStatsType type, int amount) {
		this.counter.compute(guildId, (guildKey, guildValue) -> {
			if (guildValue == null) {
				Map<ServerStatsType, Integer> guild = new HashMap<>();
				guild.put(type, amount);

				return guild;
			}

			guildValue.compute(type, (typeKey, typeValue) -> typeValue == null ? amount : typeValue + amount);

			return guildValue;
		});
	}

	public void incrementCounter(long guildId, ServerStatsType type) {
		this.addCounter(guildId, type, 1);
	}

	public void decrementCounter(long guildId, ServerStatsType type) {
		this.addCounter(guildId, type, -1);
	}

	public synchronized void clear() {
		this.counter.clear();
	}

	public synchronized List<Document> toData(LocalDateTime time) {
		List<Document> list = new ArrayList<>();

		Set<Long> guildIds = this.counter.keySet();
		for (long guildId : guildIds) {
			Map<ServerStatsType, Integer> guild = this.counter.get(guildId);

			Document data = new Document("guildId", guildId).append("time", time);

			Set<ServerStatsType> types = guild.keySet();
			for (ServerStatsType type : types) {
				data.append(type.getField(), guild.get(type));
			}

			list.add(data);
		}

		return list;
	}

}
