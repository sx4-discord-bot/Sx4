package com.sx4.bot.core;

import com.jockie.bot.core.command.impl.CommandListener;
//import com.sx4.bot.cache.GuildMessageCache;
import com.sx4.bot.message.cache.GuildMessageCache;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.message.MessageUpdateEvent;

public class Sx4CommandListener extends CommandListener {

	public void onEvent(GenericEvent event) {
		super.onEvent(event);
		
		if (event instanceof MessageUpdateEvent) {
			Message editedMessage = ((MessageUpdateEvent) event).getMessage();
			Message oldMessage = GuildMessageCache.INSTANCE.getMessageById(editedMessage.getIdLong());
			
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