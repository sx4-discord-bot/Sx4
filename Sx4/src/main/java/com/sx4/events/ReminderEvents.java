package com.sx4.events;

import java.time.Clock;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.bson.Document;

import com.mongodb.client.FindIterable;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.sx4.core.Sx4Bot;
import com.sx4.database.Database;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.sharding.ShardManager;

public class ReminderEvents {
	
	public static Map<Long, Map<Integer, ScheduledFuture<?>>> executors = new HashMap<>();
	
	public static ScheduledExecutorService scheduledExectuor = Executors.newSingleThreadScheduledExecutor();
	
	public static void putExecutor(long userId, int id, ScheduledFuture<?> executor) {
		Map<Integer, ScheduledFuture<?>> userExecutors = executors.containsKey(userId) ? executors.get(userId) : new HashMap<>();
		userExecutors.put(id, executor);
		executors.put(userId, userExecutors);
	}
	
	public static boolean cancelExecutor(long userId, int id) {
		if (executors.containsKey(userId)) {
			Map<Integer, ScheduledFuture<?>> userExecutors = executors.get(userId);
			if (userExecutors.containsKey(id)) {
				ScheduledFuture<?> executor = userExecutors.get(id);
				if (!executor.isDone()) {
					executor.cancel(false);
				}
				
				userExecutors.remove(id);
				executors.put(userId, userExecutors);
				
				return true;
			}
		}
		
		return false;
	}
	
	public static void removeUserReminder(long userId, int id, String reminder, long reminderLength, boolean repeat) {
		User user = Sx4Bot.getShardManager().getUserById(userId);
		if (user != null) {
			user.openPrivateChannel().queue(channel -> channel.sendMessage("You wanted me to remind you about **" + reminder + "**").queue(), e -> {});
			if (repeat) {
				UpdateOptions updateOptions = new UpdateOptions().arrayFilters(List.of(Filters.eq("reminder.id", id))).upsert(true);
				
				Database.get().updateUserById(user.getIdLong(), null, Updates.inc("reminder.reminders.$[reminder].remindAt", reminderLength), updateOptions, (result, exception) -> {
					if (exception != null) {
						exception.printStackTrace();
					}
				});
				
				ScheduledFuture<?> executor = ReminderEvents.scheduledExectuor.schedule(() -> ReminderEvents.removeUserReminder(userId, id, reminder, reminderLength, repeat), reminderLength, TimeUnit.SECONDS);
				ReminderEvents.putExecutor(user.getIdLong(), id, executor);
			} else {
				Database.get().updateUserById(user.getIdLong(), Updates.pull("reminder.reminders", Filters.eq("id", id)), (result, exception) -> {
					if (exception != null) {
						exception.printStackTrace();
					}
				});
				
				ReminderEvents.cancelExecutor(user.getIdLong(), id);
			}
		} else {
			Database.get().updateUserById(userId, Updates.pull("reminder.reminders", Filters.eq("id", id)), (result, exception) -> {
				if (exception != null) {
					exception.printStackTrace();
				}
			});
			
			ReminderEvents.cancelExecutor(userId, id);
		}
	}

	public static void removeUserReminder(long userId, Document data) {
		ReminderEvents.removeUserReminder(userId, data.getInteger("id"), data.getString("reminder"), data.getLong("reminderLength"), data.getBoolean("repeat"));
	}
	
	public static void ensureReminders() {
		ShardManager shardManager = Sx4Bot.getShardManager();
		FindIterable<Document> allData = Database.get().getUsers().find().projection(Projections.include("reminder.reminders"));
		allData.forEach((Document data) -> {
			List<Document> reminders = data.getEmbedded(List.of("reminder", "reminders"), Collections.emptyList());
			long timestampNow = Clock.systemUTC().instant().getEpochSecond();
			User user = shardManager.getUserById(data.getLong("_id")); 
			if (user != null) {
				for (Document reminder : reminders) {
					long timeLeft = reminder.getLong("remindAt") - timestampNow;
					if (timeLeft <= 0) {
						ReminderEvents.removeUserReminder(user.getIdLong(), reminder);
					} else {
						ScheduledFuture<?> executor = ReminderEvents.scheduledExectuor.schedule(() -> ReminderEvents.removeUserReminder(user.getIdLong(), reminder), timeLeft, TimeUnit.SECONDS);
						ReminderEvents.putExecutor(user.getIdLong(), reminder.getInteger("id"), executor);
					}
				}
			}
		});
	}
	
}
