package com.sx4.bot.hooks;

import com.sx4.bot.events.patreon.PatreonEvent;

import java.util.EventListener;

public interface PatreonListener extends EventListener {
	
	default void onPatreonEvent(PatreonEvent event) {}
	
}
