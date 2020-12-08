package com.sx4.bot.managers;

import com.mongodb.client.model.*;
import com.sx4.bot.core.Sx4;
import com.sx4.bot.database.Database;
import com.sx4.bot.entities.mod.Reason;
import com.sx4.bot.events.mod.UnmuteEvent;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
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

public class MuteManager {

	private static final MuteManager INSTANCE = new MuteManager();
	
	public static MuteManager get() {
		return MuteManager.INSTANCE;
	}
	
	private final Map<Long, Map<Long, ScheduledFuture<?>>> executors;
	
	private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
	
	private MuteManager() {
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
	
	public void extendExecutor(long guildId, long userId, Runnable runnable, long seconds) {
		if (this.executors.containsKey(guildId)) {
			Map<Long, ScheduledFuture<?>> users = this.executors.get(guildId);

			ScheduledFuture<?> oldExecutor = users.get(userId);
			if (oldExecutor != null && !oldExecutor.isDone()) {
				seconds += oldExecutor.getDelay(TimeUnit.SECONDS);
				oldExecutor.cancel(true);
			}

			users.put(userId, this.executor.schedule(runnable, seconds, TimeUnit.SECONDS));
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
	
	public void putMute(long guildId, long userId, long roleId, long seconds, boolean extend) {
		ScheduledFuture<?> executor = this.getExecutor(guildId, userId);
		if (executor != null && !executor.isDone()) {
			if (extend) {
				seconds += executor.getDelay(TimeUnit.SECONDS);
			}
			
			executor.cancel(true);
		}
		
		ScheduledFuture<?> newExecutor = this.executor.schedule(() -> this.removeMute(guildId, userId, roleId), seconds, TimeUnit.SECONDS);
		
		this.putExecutor(guildId, userId, newExecutor);
	}
	
	public void putMute(long guildId, long userId, long roleId, long seconds) {
		this.putMute(guildId, userId, roleId, seconds, false);
	}
	
	public DeleteOneModel<Document> removeMuteAndGet(long guildId, long userId, long roleId) {
		Guild guild = Sx4.get().getShardManager().getGuildById(guildId);
		if (guild == null) {
			return null;
		}
		
		Member member = guild.getMemberById(userId);
		Role role = guild.getRoleById(roleId);
		if (member != null && role != null && member.getRoles().contains(role)) {
			guild.removeRoleFromMember(member, role).reason("Mute length served").queue();
		}
		
		ModActionManager.get().onModAction(new UnmuteEvent(guild.getSelfMember(), member == null ? null : member.getUser(), new Reason("Mute length served")));
		this.deleteExecutor(guildId, userId);
		
		return new DeleteOneModel<>(Filters.and(Filters.eq("guildId", guildId), Filters.eq("userId", userId)));
	}
	
	public void removeMute(long guildId, long userId, long roleId) {
		DeleteOneModel<Document> model = this.removeMuteAndGet(guildId, userId, roleId);
		if (model != null) {
			Database.get().deleteMute(model).whenComplete(Database.exceptionally());
		}
	}
	
	public void ensureMutes() {
		Database database = Database.get();

		Map<Long, Long> roleIds = new HashMap<>();
		
		List<WriteModel<Document>> bulkData = new ArrayList<>();
		database.getMutes(Database.EMPTY_DOCUMENT, Projections.include("unmuteAt", "userId", "guildId")).forEach(data -> {
			long guildId = data.getLong("guildId");
			if (!roleIds.containsKey(guildId)) {
				long roleId = database.getGuildById(guildId, Projections.include("mute.roleId")).getEmbedded(List.of("mute", "roleId"), Long.class);
				roleIds.put(guildId, roleId);
			}

			long currentTime = Clock.systemUTC().instant().getEpochSecond(), unmuteAt = data.getLong("unmuteAt");
			if (unmuteAt > currentTime) {
				this.putMute(guildId, data.getLong("userId"), roleIds.get(guildId), unmuteAt - currentTime);
			} else {
				DeleteOneModel<Document> model = this.removeMuteAndGet(guildId, data.getLong("userId"), roleIds.get(guildId));
				if (model != null) {
					bulkData.add(model);
				}
			}
		});
		
		if (!bulkData.isEmpty()) {
			database.bulkWriteMutes(bulkData).whenComplete(Database.exceptionally());
		}
	}
	
}
