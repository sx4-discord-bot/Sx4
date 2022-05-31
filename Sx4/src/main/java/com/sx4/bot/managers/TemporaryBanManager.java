package com.sx4.bot.managers;

import com.mongodb.client.model.*;
import com.sx4.bot.core.Sx4;
import com.sx4.bot.database.mongo.MongoDatabase;
import com.sx4.bot.entities.mod.Reason;
import com.sx4.bot.entities.mod.UserReference;
import com.sx4.bot.events.mod.UnbanEvent;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.sharding.ShardManager;
import org.bson.Document;

import java.time.Clock;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class TemporaryBanManager {
	
	private final Map<Long, Map<Long, ScheduledFuture<?>>> executors;
	
	private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

	private final Sx4 bot;
	
	public TemporaryBanManager(Sx4 bot) {
		this.executors = new HashMap<>();
		this.bot = bot;
	}
	
	public ScheduledExecutorService getExecutor() {
		return this.executor;
	}
	
	public ScheduledFuture<?> getExecutor(long guildId, long userId) {
		if (this.executors.containsKey(guildId)) {
			Map<Long, ScheduledFuture<?>> users = this.executors.get(guildId);
			
			return users.get(userId);
		}
		
		return null;
	}
	
	public void putExecutor(long guildId, long userId, ScheduledFuture<?> executor) {
		if (this.executors.containsKey(guildId)) {
			Map<Long, ScheduledFuture<?>> users = this.executors.get(guildId);
			
			ScheduledFuture<?> oldExecutor = users.get(userId);
			if (oldExecutor != null && !oldExecutor.isDone()) {
				oldExecutor.cancel(true);
			}
			
			users.put(userId, executor);
		} else {
			Map<Long, ScheduledFuture<?>> users = new HashMap<>();
			users.put(userId, executor);
			
			this.executors.put(guildId, users);
		}
	}
	
	public void deleteExecutor(long guildId, long userId) {
		if (this.executors.containsKey(guildId)) {
			Map<Long, ScheduledFuture<?>> users = this.executors.get(guildId);
			
			ScheduledFuture<?> executor = users.remove(userId);
			if (executor != null && !executor.isDone()) {
				executor.cancel(true);
			}
		}
	}
	
	public void putBan(long guildId, long userId, long seconds) {
		ScheduledFuture<?> executor = this.executor.schedule(() -> this.removeBan(guildId, userId), seconds, TimeUnit.SECONDS);
		
		this.putExecutor(guildId, userId, executor);
	}

	public DeleteOneModel<Document> removeBanAndGet(long guildId, long userId, boolean automatic) {
		ShardManager shardManager = this.bot.getShardManager();

		Guild guild = shardManager.getGuildById(guildId);
		if (guild == null) {
			return null;
		}

		User user = shardManager.getUserById(userId);

		Member member = user == null ? null : guild.getMember(user);
		if (automatic && member == null) {
			guild.unban(User.fromId(userId)).reason("Ban length served").queue();
		}

		if (automatic) {
			Reason reason = new Reason("Ban length served");
			UnbanEvent event = new UnbanEvent(guild.getSelfMember(), user == null ? UserReference.fromId(userId) : user, reason);

			this.bot.getModActionManager().onModAction(event);
		}

		this.deleteExecutor(guildId, userId);

		return new DeleteOneModel<>(Filters.and(Filters.eq("guildId", guildId), Filters.eq("userId", userId)));
	}

	public DeleteOneModel<Document> removeBanAndGet(long guildId, long userId) {
		return this.removeBanAndGet(guildId, userId, true);
	}
	
	public void removeBan(long guildId, long userId, boolean automatic) {
		DeleteOneModel<Document> model = this.removeBanAndGet(guildId, userId, automatic);
		if (model != null) {
			this.bot.getMongo().deleteTemporaryBan(model.getFilter()).whenComplete(MongoDatabase.exceptionally());
		}
	}

	public void removeBan(long guildId, long userId) {
		this.removeBan(guildId, userId, true);
	}

	public void ensureBans() {
		List<WriteModel<Document>> bulkData = new ArrayList<>();
		this.bot.getMongo().getTemporaryBans(MongoDatabase.EMPTY_DOCUMENT, Projections.include("guildId", "userId", "unbanAt")).forEach(data -> {
			long currentTime = Clock.systemUTC().instant().getEpochSecond(), unbanAt = data.getLong("unbanAt");
			if (unbanAt > currentTime) {
				this.putBan(data.getLong("guildId"), data.getLong("userId"), unbanAt - currentTime);
			} else {
				DeleteOneModel<Document> model = this.removeBanAndGet(data.getLong("guildId"), data.getLong("userId"));
				if (model != null) {
					bulkData.add(model);
				}
			}
		});

		if (!bulkData.isEmpty()) {
			this.bot.getMongo().bulkWriteTemporaryBans(bulkData).whenComplete(MongoDatabase.exceptionally());
		}
	}
	
}
