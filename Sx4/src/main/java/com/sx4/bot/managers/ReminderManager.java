package com.sx4.bot.managers;

import com.mongodb.client.model.*;
import com.sx4.bot.core.Sx4;
import com.sx4.bot.database.Database;
import net.dv8tion.jda.api.entities.User;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.time.Clock;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class ReminderManager {
	
	private static final ReminderManager INSTANCE = new ReminderManager();
	
	public static ReminderManager get() {
		return ReminderManager.INSTANCE;
	}
	
	private final Map<Long, Map<ObjectId, ScheduledFuture<?>>> executors;
	
	private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

	public ReminderManager() {
		this.executors = new HashMap<>();
	}
	
	public ScheduledExecutorService getExecutor() {
		return this.executor;
	}
	
	public ScheduledFuture<?> getExecutor(long userId, ObjectId id) {
		if (this.executors.containsKey(userId)) {
			Map<ObjectId, ScheduledFuture<?>> ids = this.executors.get(userId);
			
			return ids.get(id);
		}
		
		return null;
	}
	
	public void putExecutor(long userId, ObjectId id, ScheduledFuture<?> executor) {
		if (this.executors.containsKey(userId)) {
			Map<ObjectId, ScheduledFuture<?>> ids = this.executors.get(userId);
			
			ScheduledFuture<?> oldExecutor = ids.get(id);
			if (oldExecutor != null && !oldExecutor.isDone()) {
				oldExecutor.cancel(true);
			}
			
			ids.put(id, executor);
		} else {
			Map<ObjectId, ScheduledFuture<?>> ids = new HashMap<>();
			ids.put(id, executor);
			
			this.executors.put(userId, ids);
		}
	}
	
	public void extendExecutor(long userId, ObjectId id, Runnable runnable, long seconds) {
		if (this.executors.containsKey(userId)) {
			Map<ObjectId, ScheduledFuture<?>> ids = this.executors.get(userId);
			
			ScheduledFuture<?> oldExecutor = ids.get(id);
			if (oldExecutor != null && !oldExecutor.isDone()) {
				seconds += oldExecutor.getDelay(TimeUnit.SECONDS);
				oldExecutor.cancel(true);
			}
			
			ids.put(id, this.executor.schedule(runnable, seconds, TimeUnit.SECONDS));
		} else {
			Map<ObjectId, ScheduledFuture<?>> ids = new HashMap<>();
			ids.put(id, this.executor.schedule(runnable, seconds, TimeUnit.SECONDS));
			
			this.executors.put(userId, ids);
		}
	}
	
	public void deleteExecutor(long userId, ObjectId id) {
		if (this.executors.containsKey(userId)) {
			Map<ObjectId, ScheduledFuture<?>> ids = this.executors.get(userId);
			
			ScheduledFuture<?> executor = ids.remove(id);
			if (executor != null && !executor.isDone()) {
				executor.cancel(true);
			}
		}
	}
	
	public void putReminder(long userId, long duration, Document data) {
		ScheduledFuture<?> executor = this.executor.schedule(() -> this.executeReminder(userId, data), duration, TimeUnit.SECONDS);
		
		this.putExecutor(userId, data.getObjectId("id"), executor);
	}
	
	public void executeReminder(long userId, Document data) {
		Database.get().updateUser(this.executeReminderBulk(userId, data)).whenComplete(Database.exceptionally());
	}
	
	public UpdateOneModel<Document> executeReminderBulk(long userId, Document data) {
		User user = Sx4.get().getShardManager().getUserById(userId);
		if (user != null) {
			user.openPrivateChannel()
				.flatMap(channel -> channel.sendMessageFormat("You wanted me to remind you about **%s**", data.getString("reminder")))
				.queue();
		}

		ObjectId id = data.getObjectId("id");
		long remindAt = data.getLong("remindAt"), duration = data.getLong("duration");
		if (data.get("repeat", false)) {
			data.append("remindAt", remindAt + duration);
			this.extendExecutor(userId, id, () -> this.executeReminder(userId, data), duration);
			
			UpdateOptions options = new UpdateOptions().arrayFilters(List.of(Filters.eq("reminder.id", id)));
					
			return new UpdateOneModel<>(Filters.eq("_id", userId), Updates.inc("reminder.reminders.$[reminder].remindAt", duration), options);
		} else {
			this.deleteExecutor(userId, id);
			
			return new UpdateOneModel<>(Filters.eq("_id", userId), Updates.pull("reminder.reminders", Filters.eq("id", id)));
		}
	}
	
	public void ensureReminders() {
		Database database = Database.get();
		
		List<WriteModel<Document>> bulkData = new ArrayList<>();
		database.getUsers(Filters.elemMatch("reminder.reminders", Filters.exists("id")), Projections.include("reminder.reminders")).forEach(data -> {
			long userId = data.getLong("_id");
			
			List<Document> reminders = data.getEmbedded(List.of("reminder", "reminders"), Collections.emptyList());
			for (Document reminder : reminders) {
				ObjectId id = reminder.getObjectId("id");
				
				long remindAt = reminder.getLong("remindAt"), currentTime = Clock.systemUTC().instant().getEpochSecond();
				if (remindAt > currentTime) {
					ScheduledFuture<?> executor = this.executor.schedule(() -> this.executeReminder(userId, reminder), remindAt - currentTime, TimeUnit.SECONDS);
					
					this.putExecutor(userId, id, executor);
				} else {
					bulkData.add(this.executeReminderBulk(userId, reminder));
				}
			}
		});
		
		if (!bulkData.isEmpty()) {
			database.bulkWriteUsers(bulkData).whenComplete(Database.exceptionally());
		}
	}
	
}
