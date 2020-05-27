package com.sx4.bot.waiter;

import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class WaiterHandler extends ListenerAdapter {
	
	private final WaiterManager manager = WaiterManager.get();

	public void onGenericEvent(GenericEvent event) {
		Class<?> clazz = event.getClass();
		
		while (clazz != null) {
			this.manager.checkWaiters(event, clazz);
			
			clazz = clazz.getSuperclass();
		}
	}
	
}
