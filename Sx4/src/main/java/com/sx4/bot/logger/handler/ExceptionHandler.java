package com.sx4.bot.logger.handler;

import java.time.LocalDateTime;

import com.sx4.bot.core.Sx4Bot;

import net.dv8tion.jda.api.events.ExceptionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class ExceptionHandler extends ListenerAdapter {
	
	public void onException(ExceptionEvent event) {
		System.err.println("[" + LocalDateTime.now().format(Sx4Bot.getTimeFormatter()) + "] [onException]");
		
		event.getCause().printStackTrace();
	}
}