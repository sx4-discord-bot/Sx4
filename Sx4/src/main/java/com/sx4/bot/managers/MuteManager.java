package com.sx4.bot.managers;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.sx4.bot.core.Sx4Bot;
import com.sx4.bot.database.Database;
import com.sx4.bot.entities.mod.Reason;
import com.sx4.bot.events.mod.UnmuteEvent;
import com.sx4.bot.utility.ExceptionUtility;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;

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
	
	public void removeMute(long guildId, long userId, long roleId) {
		Guild guild = Sx4Bot.getShardManager().getGuildById(guildId);
		if (guild == null) {
			return;
		}
		
		Member member = guild.getMemberById(userId);
		Role role = guild.getRoleById(roleId);
		if (member != null && role != null && member.getRoles().contains(role)) {
			guild.removeRoleFromMember(member, role).reason("Mute length served").queue();
		}
		
		Database.get().updateGuildById(guildId, Updates.pull("mute.users", Filters.eq("id", userId))).whenComplete((result, exception) -> {
			if (exception != null) {
				exception.printStackTrace();
				ExceptionUtility.sendErrorMessage(exception);
			} 
			
			Sx4Bot.getModActionManager().onModAction(new UnmuteEvent(guild.getSelfMember(), member.getUser(), new Reason("Mute length served")));
			this.deleteExecutor(guildId, userId);
		});
	}
	
}
