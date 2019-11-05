package com.sx4.bot.events;

import java.time.Clock;
import java.util.ArrayList;
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
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.WriteModel;
import com.sx4.bot.core.Sx4Bot;
import com.sx4.bot.database.Database;

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
	
	public static UpdateOneModel<Document> removeUserReminderAndGet(long userId, int id, String reminder, long duration, boolean repeat) {
		User user = Sx4Bot.getShardManager().getUserById(userId);
		if (user != null) {
			user.openPrivateChannel().queue(channel -> channel.sendMessage("You wanted me to remind you about **" + reminder + "**").queue(), e -> {});
		}
		
		if (repeat) {
			ScheduledFuture<?> executor = ReminderEvents.scheduledExectuor.schedule(() -> ReminderEvents.removeUserReminder(userId, id, reminder, duration, repeat), duration, TimeUnit.SECONDS);
			ReminderEvents.putExecutor(userId, id, executor);
			
			UpdateOptions updateOptions = new UpdateOptions().arrayFilters(List.of(Filters.eq("reminder.id", id)));
			return new UpdateOneModel<>(Filters.eq("_id", userId), Updates.inc("reminder.reminders.$[reminder].remindAt", duration), updateOptions);
		} else {
			ReminderEvents.cancelExecutor(userId, id);
			
			return new UpdateOneModel<>(Filters.eq("_id", userId), Updates.pull("reminder.reminders", Filters.eq("id", id)));
		}
	}
	
	public static UpdateOneModel<Document> removeUserReminderAndGet(long userId, Document data) {
		return ReminderEvents.removeUserReminderAndGet(userId, data.getInteger("id"), data.getString("reminder"), data.getLong("duration"), data.getBoolean("repeat"));
	}
	
	public static void removeUserReminder(long userId, int id, String reminder, long duration, boolean repeat) {
		Database.get().updateUserById(ReminderEvents.removeUserReminderAndGet(userId, id, reminder, duration, repeat), (result, exception) -> {
			if (exception != null) {
				exception.printStackTrace();
			}
		});
	}

	public static void removeUserReminder(long userId, Document data) {
		ReminderEvents.removeUserReminder(userId, data.getInteger("id"), data.getString("reminder"), data.getLong("duration"), data.getBoolean("repeat"));
	}
	
	public static void ensureReminders() {
		ShardManager shardManager = Sx4Bot.getShardManager();
		FindIterable<Document> allData = Database.get().getUsers().find(Filters.exists("reminder.reminders")).projection(Projections.include("reminder.reminders"));
		
		List<WriteModel<Document>> bulkData = new ArrayList<>();
		for (Document data : allData) {
			try {
				List<Document> reminders = data.getEmbedded(List.of("reminder", "reminders"), Collections.emptyList());
				long timestampNow = Clock.systemUTC().instant().getEpochSecond();
				User user = shardManager.getUserById(data.getLong("_id")); 
				if (user != null) {
					for (Document reminder : reminders) {
						long timeLeft = reminder.getLong("remindAt") - timestampNow;
						if (timeLeft <= 0) {
							bulkData.add(ReminderEvents.removeUserReminderAndGet(user.getIdLong(), reminder));
						} else {
							ScheduledFuture<?> executor = ReminderEvents.scheduledExectuor.schedule(() -> ReminderEvents.removeUserReminder(user.getIdLong(), reminder), timeLeft, TimeUnit.SECONDS);
							ReminderEvents.putExecutor(user.getIdLong(), reminder.getInteger("id"), executor);
						}
					}
				}
			} catch(Throwable e) {
				e.printStackTrace();
			}
		}
		
		if (!bulkData.isEmpty()) {
			Database.get().bulkWriteUsers(bulkData, (result, exception) -> {
				if (exception != null) {
					exception.printStackTrace();
				}
			});
		}
	}
	
}
