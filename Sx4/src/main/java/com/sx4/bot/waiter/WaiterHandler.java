package com.sx4.bot.waiter;

import com.sx4.bot.core.Sx4;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.hooks.EventListener;

public class WaiterHandler implements EventListener {

	private final Sx4 bot;

	public WaiterHandler(Sx4 bot) {
		this.bot = bot;
	}

	public void onEvent(GenericEvent event) {
		Class<?> clazz = event.getClass();
		
		while (clazz != null) {
			this.bot.getWaiterManager().checkWaiters(event, clazz);
			
			clazz = clazz.getSuperclass();
		}
	}
	
}
