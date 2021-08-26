package com.sx4.bot.waiter;

import com.sx4.bot.waiter.Waiter.CancelType;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class WaiterManager {
	
	private final List<Waiter<?>> waiters;
	private final Map<Long, Map<Long, Waiter<?>>> uniqueWaiters;
	
	private final Map<Waiter<?>, ScheduledFuture<?>> executors;
	
	private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
	
	public WaiterManager() {
		this.uniqueWaiters = new HashMap<>();
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
		
		this.executors.put(waiter, this.executor.schedule(waiter::timeout, waiter.getTimeout(), TimeUnit.SECONDS));
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
		if (waiter.isUnique() && waiter.getEvent().isAssignableFrom(MessageReceivedEvent.class)) {
			Map<Long, Waiter<?>> users = this.uniqueWaiters.get(waiter.getChannelId());
			if (users != null) {
				Waiter<?> oldWaiter = users.remove(waiter.getAuthorId());
				if (oldWaiter != null) {
					oldWaiter.cancel(null, CancelType.UNIQUE);
				}
				
				users.put(waiter.getAuthorId(), waiter);
				
				return;
			}
			
			users = new HashMap<>();
			users.put(waiter.getAuthorId(), waiter);
			
			this.uniqueWaiters.put(waiter.getChannelId(), users);
		} else {
			this.waiters.add(waiter);
		}
	}
	
	public void removeWaiter(Waiter<?> waiter) {
		if (waiter.isUnique()) {
			Map<Long, Waiter<?>> users = this.uniqueWaiters.get(waiter.getChannelId());
			if (users != null) {
				users.remove(waiter.getAuthorId());
			}
		} else {
			this.waiters.remove(waiter);
		}
	}
	
	public void checkWaiters(GenericEvent event, Class<?> clazz) {
		if (event instanceof MessageReceivedEvent) {
			MessageReceivedEvent messageEvent = (MessageReceivedEvent) event;
			
			Map<Long, Waiter<?>> users = this.uniqueWaiters.get(messageEvent.getChannel().getIdLong());
			if (users != null) {
				Waiter<?> waiter = users.get(messageEvent.getAuthor().getIdLong());
				if (waiter != null && waiter.getEvent() == clazz) {
					if (waiter.testPredicate(event)) {
						waiter.execute(event);
					}
					
					if (waiter.testCancelPredicate(event)) {
						waiter.cancel(event, CancelType.USER);
					}
				}
			}
		}
		
		for (Waiter<?> waiter : new ArrayList<>(this.waiters)) {
			if (waiter.getEvent() != clazz) {
				continue;
			}
			
			if (waiter.testPredicate(event)) {
				waiter.execute(event);
			}
			
			if (waiter.testCancelPredicate(event)) {
				waiter.cancel(event, CancelType.USER);
			}
		}
	}
	
}
