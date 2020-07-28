package com.sx4.bot.managers;

import com.mongodb.client.model.*;
import com.sx4.bot.database.Database;
import com.sx4.bot.entities.reminder.Reminder;
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
		ScheduledFuture<?> executor = this.executor.schedule(() -> this.executeReminder(new Reminder(userId, data)), duration, TimeUnit.SECONDS);
		
		this.putExecutor(userId, data.getObjectId("id"), executor);
	}
	
	public void executeReminder(Reminder reminder) {
		Database.get().updateUser(this.executeReminderBulk(reminder)).whenComplete(Database.defaultHandler());
	}
	
	public UpdateOneModel<Document> executeReminderBulk(Reminder reminder) {
		long userId = reminder.getUserId();
		
		User user = reminder.getUser();
		if (user != null) {
			user.openPrivateChannel()
				.flatMap(channel -> channel.sendMessageFormat("You wanted me to remind you about **%s**", reminder.getReminder()))
				.queue();
		}
		
		if (reminder.isRepeated()) {
			this.extendExecutor(userId, reminder.getId(), () -> this.executeReminder(reminder.extend()), reminder.getDuration());
			
			UpdateOptions options = new UpdateOptions().arrayFilters(List.of(Filters.eq("reminder.id", reminder.getId())));
					
			return new UpdateOneModel<>(Filters.eq("_id", userId), Updates.inc("reminder.reminders.$[reminder].remindAt", reminder.getDuration()), options);
		} else {
			this.deleteExecutor(userId, reminder.getId());
			
			return new UpdateOneModel<>(Filters.eq("_id", userId), Updates.pull("reminder.reminders", Filters.eq("id", reminder.getId())));
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
					ScheduledFuture<?> executor = this.executor.schedule(() -> this.executeReminder(new Reminder(userId, reminder)), remindAt - currentTime, TimeUnit.SECONDS);
					
					this.putExecutor(userId, id, executor);
				} else {
					bulkData.add(this.executeReminderBulk(new Reminder(userId, reminder)));
				}
			}
		});
		
		if (!bulkData.isEmpty()) {
			database.bulkWriteUsers(bulkData).whenComplete(Database.defaultHandler());
		}
	}
	
}
