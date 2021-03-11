package com.sx4.bot.waiter;

import com.sx4.bot.core.Sx4;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class WaiterHandler extends ListenerAdapter {

	private final Sx4 bot;

	public WaiterHandler(Sx4 bot) {
		this.bot = bot;
	}

	public void onGenericEvent(GenericEvent event) {
		Class<?> clazz = event.getClass();
		
		while (clazz != null) {
			this.bot.getWaiterManager().checkWaiters(event, clazz);
			
			clazz = clazz.getSuperclass();
		}
	}
	
}
