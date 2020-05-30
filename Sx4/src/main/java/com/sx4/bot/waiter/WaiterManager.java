package com.sx4.bot.waiter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import net.dv8tion.jda.api.events.GenericEvent;

public class WaiterManager {

	private static final WaiterManager INSTANCE = new WaiterManager();
	
	public static WaiterManager get() {
		return WaiterManager.INSTANCE;
	}
	
	private final List<Waiter<?>> waiters;
	private final Map<Waiter<?>, ScheduledFuture<?>> executors;
	
	private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
	
	private WaiterManager() {
		this.waiters = new ArrayList<>();
		this.executors = new HashMap<>();
	}
	
	public void setTimeout(Waiter<?> waiter) {
		if (this.executors.containsKey(waiter)) {
			ScheduledFuture<?> executor = this.executors.remove(waiter);
			if (executor != null && !executor.isDone()) {
				executor.cancel(true);
			}
		}
		
		this.executors.put(waiter, this.executor.schedule(() -> waiter.timeout(), waiter.getTimeout(), TimeUnit.SECONDS));
	}
	
	public void cancelTimeout(Waiter<?> waiter) {
		if (this.executors.containsKey(waiter)) {
			ScheduledFuture<?> executor = this.executors.remove(waiter);
			if (executor != null && !executor.isDone()) {
				executor.cancel(true);
			}
		}
	}
	
	public void addWaiter(Waiter<?> waiter) {
		this.waiters.add(waiter);
	}
	
	public void removeWaiter(Waiter<?> waiter) {
		this.waiters.remove(waiter);
	}
	
	public List<Waiter<?>> getWaiters(Class<?> clazz) {
		return this.waiters.stream()
			.filter(waiter -> waiter.getEvent() == clazz)
			.collect(Collectors.toList());
	}
	
	public void checkWaiters(GenericEvent event, Class<?> clazz) {
		for (Waiter<?> waiter : new ArrayList<>(this.waiters)) {
			if (waiter.getEvent() != clazz) {
				continue;
			}
			
			if (waiter.testPredicate(event)) {
				waiter.execute(event);
			}
			
			if (waiter.testCancelPredicate(event)) {
				waiter.cancel();
			}
		}
	}
	
}
