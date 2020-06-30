package com.sx4.bot.managers;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.bson.Document;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.WriteModel;
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
	
	public UpdateOneModel<Document> removeMuteAndGet(long guildId, long userId, long roleId) {
		Guild guild = Sx4Bot.getShardManager().getGuildById(guildId);
		if (guild == null) {
			return null;
		}
		
		Member member = guild.getMemberById(userId);
		Role role = guild.getRoleById(roleId);
		if (member != null && role != null && member.getRoles().contains(role)) {
			guild.removeRoleFromMember(member, role).reason("Mute length served").queue();
		}
		
		ModActionManager.get().onModAction(new UnmuteEvent(guild.getSelfMember(), member.getUser(), new Reason("Mute length served")));
		this.deleteExecutor(guildId, userId);
		
		return new UpdateOneModel<>(Filters.eq("_id", guildId), Updates.pull("mute.users", Filters.eq("id", userId)));
	}
	
	public void removeMute(long guildId, long userId, long roleId) {
		UpdateOneModel<Document> model = this.removeMuteAndGet(guildId, userId, roleId);
		if (model != null) {
			Database.get().updateGuild(model).whenComplete((result, exception) -> ExceptionUtility.sendErrorMessage(exception));
		}
	}
	
	public void ensureMutes() {
		Database database = Database.get();
		
		List<WriteModel<Document>> bulkData = new ArrayList<>();
		database.getGuilds(Filters.elemMatch("mute.users", Filters.exists("id")), Projections.include("mute.users", "mute.role")).forEach(data -> {
			Document mute = data.get("mute", Document.class);
			long roleId = mute.get("roleId", 0L);
			
			List<Document> users = mute.getList("users", Document.class);
			for (Document user : users) {
				long currentTime = Clock.systemUTC().instant().getEpochSecond(), unmuteAt = user.get("unmuteAt", 0L);
				if (unmuteAt > currentTime) {
					this.putMute(data.get("_id", 0L), user.get("id", 0L), roleId, unmuteAt - currentTime);
				} else {
					UpdateOneModel<Document> model = this.removeMuteAndGet(data.get("_id", 0L), user.get("id", 0L), roleId);
					if (model != null) {
						bulkData.add(model);
					}
				}
			}
		});
		
		if (!bulkData.isEmpty()) {
			database.bulkWriteGuilds(bulkData).whenComplete((result, exception) -> ExceptionUtility.sendErrorMessage(exception));
		}
	}
	
}
