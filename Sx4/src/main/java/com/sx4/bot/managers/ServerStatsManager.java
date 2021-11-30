package com.sx4.bot.managers;

import com.sx4.bot.core.Sx4;
import com.sx4.bot.database.mongo.MongoDatabase;
import com.sx4.bot.entities.info.ServerStatsType;
import gnu.trove.map.TLongObjectMap;
import gnu.trove.map.hash.TLongObjectHashMap;
import gnu.trove.set.TLongSet;
import org.bson.Document;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ServerStatsManager {

	private final TLongObjectMap<Map<ServerStatsType, Integer>> counter;
	private Date lastUpdate;

	private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

	private final Sx4 bot;

	public ServerStatsManager(Sx4 bot) {
		this.counter = new TLongObjectHashMap<>();
		this.bot = bot;
		this.initialize();
	}

	public void initialize() {
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		OffsetDateTime nextHour = now.plusHours(1).withSecond(0).withMinute(0).withNano(0);

		this.executor.scheduleAtFixedRate(() -> {
			List<Document> data = this.toData(this.lastUpdate = new Date());
			this.clear();

			if (!data.isEmpty()) {
				this.bot.getMongo().insertManyServerStats(data).whenComplete(MongoDatabase.exceptionally());
			}
		}, Duration.between(now, nextHour).toSeconds(), 3600, TimeUnit.SECONDS);
	}

	public Date getLastUpdate() {
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
		Map<ServerStatsType, Integer> guild = this.counter.get(guildId);
		if (guild == null) {
			guild = new HashMap<>();
			this.counter.put(guildId, guild);
		}

		int currentAmount = guild.getOrDefault(type, 0);
		guild.put(type, currentAmount + amount);
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

	public synchronized List<Document> toData(Date time) {
		List<Document> list = new ArrayList<>();

		TLongSet guildIds = this.counter.keySet();
		guildIds.forEach(guildId -> {
			Map<ServerStatsType, Integer> guild = this.counter.get(guildId);

			Document data = new Document("guildId", guildId).append("time", time);

			Set<ServerStatsType> types = guild.keySet();
			for (ServerStatsType type : types) {
				data.append(type.getField(), guild.get(type));
			}

			list.add(data);

			return true;
		});

		return list;
	}

}
