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
	
	private final Map<ObjectId, ScheduledFuture<?>> executors;
	
	private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

	private final Sx4 bot;

	public ReminderManager(Sx4 bot) {
		this.executors = new HashMap<>();
		this.bot = bot;
	}
	
	public ScheduledExecutorService getExecutor() {
		return this.executor;
	}
	
	public ScheduledFuture<?> getExecutor(ObjectId id) {
		return this.executors.get(id);
	}
	
	public void putExecutor(ObjectId id, ScheduledFuture<?> executor) {
		ScheduledFuture<?> oldExecutor = this.getExecutor(id);
		if (oldExecutor != null && !oldExecutor.isDone()) {
			oldExecutor.cancel(true);
		}

		this.executors.put(id, executor);
	}
	
	public void extendExecutor(ObjectId id, Runnable runnable, long seconds) {
		ScheduledFuture<?> oldExecutor = this.getExecutor(id);
		if (oldExecutor != null && !oldExecutor.isDone()) {
			seconds += oldExecutor.getDelay(TimeUnit.SECONDS);
			oldExecutor.cancel(true);
		}
			
		this.executors.put(id, this.executor.schedule(runnable, seconds, TimeUnit.SECONDS));
	}
	
	public void deleteExecutor(ObjectId id) {
		ScheduledFuture<?> oldExecutor = this.executors.remove(id);
		if (oldExecutor != null && !oldExecutor.isDone()) {
			oldExecutor.cancel(true);
		}
	}
	
	public void putReminder(long duration, Document data) {
		ScheduledFuture<?> executor = this.executor.schedule(() -> this.executeReminder(data), duration, TimeUnit.SECONDS);
		
		this.putExecutor(data.getObjectId("id"), executor);
	}
	
	public void executeReminder(Document data) {
		WriteModel<Document> model = this.executeReminderBulk(data);
		if (model instanceof UpdateOneModel) {
			this.bot.getDatabase().updateReminder((UpdateOneModel<Document>) model).whenComplete(Database.exceptionally(this.bot.getShardManager()));
		} else {
			this.bot.getDatabase().deleteReminder((DeleteOneModel<Document>) model).whenComplete(Database.exceptionally(this.bot.getShardManager()));
		}
	}
	
	public WriteModel<Document> executeReminderBulk(Document data) {
		User user = this.bot.getShardManager().getUserById(data.getLong("userId"));
		if (user != null) {
			user.openPrivateChannel()
				.flatMap(channel -> channel.sendMessageFormat("You wanted me to remind you about **%s**", data.getString("reminder")))
				.queue();
		}

		ObjectId id = data.getObjectId("_id");
		long remindAt = data.getLong("remindAt"), duration = data.getLong("duration");
		if (data.get("repeat", false)) {
			data.append("remindAt", remindAt + duration);
			this.extendExecutor(id, () -> this.executeReminder(data), duration);

			return new UpdateOneModel<>(Filters.eq("_id", id), Updates.inc("remindAt", duration));
		} else {
			this.deleteExecutor(id);
			
			return new DeleteOneModel<>(Filters.eq("_id", id));
		}
	}
	
	public void ensureReminders() {
		List<WriteModel<Document>> bulkData = new ArrayList<>();
		this.bot.getDatabase().getReminders(Database.EMPTY_DOCUMENT, Database.EMPTY_DOCUMENT).forEach(data -> {
			ObjectId id = data.getObjectId("_id");

			long remindAt = data.getLong("remindAt"), currentTime = Clock.systemUTC().instant().getEpochSecond();
			if (remindAt > currentTime) {
				ScheduledFuture<?> executor = this.executor.schedule(() -> this.executeReminder(data), remindAt - currentTime, TimeUnit.SECONDS);

				this.putExecutor(id, executor);
			} else {
				bulkData.add(this.executeReminderBulk(data));
			}
		});
		
		if (!bulkData.isEmpty()) {
			this.bot.getDatabase().bulkWriteReminders(bulkData).whenComplete(Database.exceptionally(this.bot.getShardManager()));
		}
	}
	
}
