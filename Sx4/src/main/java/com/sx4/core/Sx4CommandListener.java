package com.sx4.core;

import com.jockie.bot.core.command.impl.CommandListener;
import com.sx4.logger.handler.GuildMessageCache;

import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.events.Event;
import net.dv8tion.jda.core.events.message.MessageUpdateEvent;

public class Sx4CommandListener extends CommandListener {

	public void onEvent(Event event) {
		if (event instanceof MessageUpdateEvent) {
			System.out.println(((MessageUpdateEvent) event).getMessage());
		}
		
		super.onEvent(event);
		
		if (event instanceof MessageUpdateEvent) {
			Message editedMessage = ((MessageUpdateEvent) event).getMessage();
			Message oldMessage = GuildMessageCache.INSTANCE.getMessageById(editedMessage.getId());
			
			if (oldMessage == null) {
				return;
			}
			
			if ((oldMessage.isPinned() && !editedMessage.isPinned()) || (!oldMessage.isPinned() && editedMessage.isPinned())) {
				return;
			}
			
			this.parse(editedMessage);
		}
	}
	
}
