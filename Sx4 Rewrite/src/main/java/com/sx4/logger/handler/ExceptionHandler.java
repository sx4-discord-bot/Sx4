package com.sx4.logger.handler;

import java.time.LocalDateTime;

import com.sx4.core.Sx4Bot;

import net.dv8tion.jda.core.events.ExceptionEvent;
import net.dv8tion.jda.core.hooks.ListenerAdapter;

public class ExceptionHandler extends ListenerAdapter {
	
	public void onException(ExceptionEvent event) {
		System.err.println("[" + LocalDateTime.now().format(Sx4Bot.getTimeFormatter()) + "] [onException]");
		
		event.getCause().printStackTrace();
	}
}