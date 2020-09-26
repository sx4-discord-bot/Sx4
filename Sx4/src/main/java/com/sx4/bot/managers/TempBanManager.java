package com.sx4.bot.managers;

import com.mongodb.client.model.*;
import com.sx4.bot.core.Sx4;
import com.sx4.bot.database.Database;
import com.sx4.bot.entities.mod.Reason;
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

public class TempBanManager {

private static final TempBanManager INSTANCE = new TempBanManager();
	
	public static TempBanManager get() {
		return TempBanManager.INSTANCE;
	}
	
	private final Map<Long, Map<Long, ScheduledFuture<?>>> executors;
	
	private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
	
	private TempBanManager() {
		this.executors = new HashMap<>();
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

	public UpdateOneModel<Document> removeBanAndGet(long guildId, long userId) {
		ShardManager shardManager = Sx4.get().getShardManager();

		Guild guild = shardManager.getGuildById(guildId);
		if (guild == null) {
			return null;
		}

		User user = shardManager.getUserById(userId);

		Member member = user == null ? null : guild.getMember(user);
		if (member == null) {
			guild.unban(String.valueOf(userId)).reason("Ban length served").queue();
		}

		ModActionManager.get().onModAction(new UnbanEvent(guild.getSelfMember(), user, new Reason("Ban length served")));
		this.deleteExecutor(guildId, userId);

		return new UpdateOneModel<>(Filters.eq("_id", guildId), Updates.pull("tempBan.users", Filters.eq("id", userId)));
	}
	
	public void removeBan(long guildId, long userId) {
		UpdateOneModel<Document> model = this.removeBanAndGet(guildId, userId);
		if (model != null) {
			Database.get().updateGuild(model).whenComplete(Database.exceptionally());
		}
	}

	public void ensureBans() {
		Database database = Database.get();

		List<WriteModel<Document>> bulkData = new ArrayList<>();
		database.getGuilds(Filters.elemMatch("tempBan.users", Filters.exists("id")), Projections.include("tempBan.users")).forEach(data -> {
			List<Document> users = data.getEmbedded(List.of("tempBan", "users"), Collections.emptyList());
			for (Document user : users) {
				long currentTime = Clock.systemUTC().instant().getEpochSecond(), unbanAt = user.getLong("unbanAt");
				if (unbanAt > currentTime) {
					this.putBan(data.get("_id", 0L), user.getLong("id"), unbanAt - currentTime);
				} else {
					UpdateOneModel<Document> model = this.removeBanAndGet(data.get("_id", 0L), user.getLong("id"));
					if (model != null) {
						bulkData.add(model);
					}
				}
			}
		});

		if (!bulkData.isEmpty()) {
			database.bulkWriteGuilds(bulkData).whenComplete(Database.exceptionally());
		}
	}
	
}
