package com.sx4.events;

import static com.rethinkdb.RethinkDB.r;

import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import com.rethinkdb.net.Cursor;
import com.sx4.core.Sx4Bot;

import net.dv8tion.jda.bot.sharding.ShardManager;
import net.dv8tion.jda.core.entities.User;

public class ReminderEvents {
	
	public static Map<String, Map<Long, ScheduledFuture<?>>> executors = new HashMap<>();
	
	public static ScheduledExecutorService scheduledExectuor = Executors.newSingleThreadScheduledExecutor();
	
	public static void putExecutor(String userId, long id, ScheduledFuture<?> executor) {
		Map<Long, ScheduledFuture<?>> userExecutors = executors.containsKey(userId) ? executors.get(userId) : new HashMap<>();
		userExecutors.put(id, executor);
		executors.put(userId, userExecutors);
	}
	
	public static boolean cancelExecutor(String userId, long id) {
		if (executors.containsKey(userId)) {
			Map<Long, ScheduledFuture<?>> userExecutors = executors.get(userId);
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
	
	public static void removeUserReminder(User user, long id, String reminder, long reminderLength, boolean repeat) {
		user.openPrivateChannel().queue(channel -> channel.sendMessage("You wanted me to remind you about **" + reminder + "**").queue(), e -> {});
		if (repeat) {
			long timestampNow = Clock.systemUTC().instant().getEpochSecond();
			r.table("reminders").get(user.getId()).update(row -> r.hashMap("reminders", row.g("reminders").map(d -> d.without("remind_at").merge(r.hashMap("remind_at", d.g("reminder_length").add(timestampNow)))))).runNoReply(Sx4Bot.getConnection());
			ScheduledFuture<?> executor = ReminderEvents.scheduledExectuor.schedule(() -> ReminderEvents.removeUserReminder(user, id, reminder, reminderLength, repeat), reminderLength, TimeUnit.SECONDS);
			ReminderEvents.putExecutor(user.getId(), id, executor);
		} else {
			r.table("reminders").get(user.getId()).update(row -> r.hashMap("reminders", row.g("reminders").filter(d -> d.g("id").ne(id)))).runNoReply(Sx4Bot.getConnection());
			ReminderEvents.cancelExecutor(user.getId(), id);
		}
	}

	public static void removeUserReminder(User user, Map<String, Object> data) {
		ReminderEvents.removeUserReminder(user, (long) data.get("id"), (String) data.get("reminder"), (long) data.get("reminder_length"), (boolean) data.get("repeat"));
	}
	
	@SuppressWarnings("unchecked")
	public static void ensureReminders() {
		ShardManager shardManager = Sx4Bot.getShardManager();
		Cursor<Map<String, Object>> cursor = r.table("reminders").run(Sx4Bot.getConnection());
		List<Map<String, Object>> data = cursor.toList();
		
		long timestampNow = Clock.systemUTC().instant().getEpochSecond();
		for (Map<String, Object> userData : data) {
			User user = shardManager.getUserById((String) userData.get("id")); 
			if (user != null) {
				List<Map<String, Object>> reminders = (List<Map<String, Object>>) userData.get("reminders");
				for (Map<String, Object> reminder : reminders) {
					long timeLeft = (long) reminder.get("remind_at") - timestampNow;
					if (timeLeft <= 0) {
						ReminderEvents.removeUserReminder(user, reminder);
					} else {
						ScheduledFuture<?> executor = ReminderEvents.scheduledExectuor.schedule(() -> ReminderEvents.removeUserReminder(user, reminder), timeLeft, TimeUnit.SECONDS);
						ReminderEvents.putExecutor(user.getId(), (long) reminder.get("id"), executor);
					}
				}
			}
		}
	}
	
}
