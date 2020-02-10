package com.sx4.bot.managers;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.bson.types.ObjectId;

public class MuteManager {

	private static final MuteManager INSTANCE = new MuteManager();
	
	public static MuteManager get() {
		return MuteManager.INSTANCE;
	}
	
	private final Map<ObjectId, ScheduledFuture<?>> executors;
	
	private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
	
	private MuteManager() {
		this.executors = new HashMap<>();
	}
	
	public ScheduledExecutorService getExecutor() {
		return this.executor;
	}
	
	public ScheduledFuture<?> getExecutor(ObjectId id) {
		return this.executors.get(id);
	}
	
	public void putExecutor(ObjectId id, ScheduledFuture<?> executor) {
		this.executors.put(id, executor);
	}
	
	public void extendExecutor(ObjectId id, Runnable runnable, long seconds) {
		ScheduledFuture<?> executor = this.executors.remove(id);
		if (executor != null && !executor.isDone()) {
			seconds += executor.getDelay(TimeUnit.SECONDS);
			executor.cancel(true);
		}
		
		this.executors.put(id, this.executor.schedule(runnable, seconds, TimeUnit.SECONDS));
	}
	
	public void deleteExecutor(ObjectId id) {
		ScheduledFuture<?> executor = this.executors.remove(id);
		if (executor != null && !executor.isDone()) {
			executor.cancel(true);
		}
	}
	
}
