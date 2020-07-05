package com.sx4.bot.core;

import org.bson.Document;

import com.jockie.bot.core.command.impl.CommandListener;
import com.sx4.bot.database.Database;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.message.MessageUpdateEvent;

public class Sx4CommandListener extends CommandListener {

	public void onEvent(GenericEvent event) {
		super.onEvent(event);
		
		if (event instanceof MessageUpdateEvent) {
			Message editedMessage = ((MessageUpdateEvent) event).getMessage();
			Document oldMessage = Database.get().getMessageById(editedMessage.getIdLong());
			
			if (oldMessage == null) {
				return;
			}
			
			if ((oldMessage.getBoolean("pinned") && !editedMessage.isPinned()) || (!oldMessage.getBoolean("pinned") && editedMessage.isPinned())) {
				return;
			}
			
			this.handle(editedMessage);
		}
	}
	
}