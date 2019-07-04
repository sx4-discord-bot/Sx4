package com.sx4.events;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;

import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class EventWaiterEvents extends ListenerAdapter {
	
	private ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
	
	@SuppressWarnings("rawtypes")
	private Map<Class<?>, List<WaitEvent>> waiters = new HashMap<>();
	
	public EventWaiterEvents() {}
	
	@SuppressWarnings("rawtypes")
	public <Type extends GenericEvent> void waitForEvent(Class<Type> classType, Predicate<Type> check, Consumer<Type> action, long timeout, TimeUnit unit, Runnable timeoutAction) {
		WaitEvent<Type> waitEvent = new WaitEvent<>(check, action);
		
		List<WaitEvent> list = this.waiters.computeIfAbsent(classType, c -> new ArrayList<>());
		list.add(waitEvent);
		
		executor.schedule(() -> {
			if (list.remove(waitEvent) && timeoutAction != null) {
				timeoutAction.run();
			}
		}, timeout, unit);
	}
	
	@SuppressWarnings({"unchecked", "rawtypes"})
	public void onGenericEvent(GenericEvent event) {
		Class eventClass = event.getClass();
		
		while (eventClass != null) {
			if (this.waiters.containsKey(eventClass)) {
				List<WaitEvent> actions = this.waiters.get(eventClass);
				for (WaitEvent action : new ArrayList<>(actions)) {
					boolean successful = action.execute(event);
					if (successful) {
						actions.remove(action);
					}
				}
			}
			
			eventClass = eventClass.getSuperclass();
		}
	}
	
	private class WaitEvent<Type extends GenericEvent> {
		
		private Predicate<Type> check;
		private Consumer<Type> action;
		
		public WaitEvent(Predicate<Type> check, Consumer<Type> action) {
			this.check = check;
			this.action = action;
		}
		
		public boolean execute(Type event) {
			if (check.test(event)) {
				action.accept(event);
				return true;
			}
			
			return false;
		}
		
	}
	
}
