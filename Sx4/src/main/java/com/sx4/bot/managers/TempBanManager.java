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
import com.sx4.bot.events.mod.UnbanEvent;
import com.sx4.bot.utility.ExceptionUtility;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;

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
	
	public void removeBan(long guildId, long userId) {
		Guild guild = Sx4Bot.getShardManager().getGuildById(guildId);
		if (guild == null) {
			return;
		}
		
		Member member = guild.getMemberById(userId);
		if (member == null) {
			guild.unban(String.valueOf(userId)).reason("Ban length served").queue();
		}
		
		Database.get().updateGuildById(guildId, Updates.pull("tempBan.users", Filters.eq("id", userId))).whenComplete((result, exception) -> {
			if (ExceptionUtility.sendErrorMessage(exception)) {
				return;
			} 
			
			ModActionManager.get().onModAction(new UnbanEvent(guild.getSelfMember(), member.getUser(), new Reason("Ban length served")));
			this.deleteExecutor(guildId, userId);
		});
	}
	
}
