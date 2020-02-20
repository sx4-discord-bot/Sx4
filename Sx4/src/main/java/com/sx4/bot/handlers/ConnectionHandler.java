package com.sx4.bot.handlers;

import net.dv8tion.jda.api.events.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class ConnectionHandler extends ListenerAdapter {

	public void onReady(ReadyEvent event) {
		YouTubeHandler.get().ensureWebhooks();
	}
	
}
