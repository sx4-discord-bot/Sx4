package com.sx4.bot.core;

import org.bson.Document;

import com.jockie.bot.core.command.impl.CommandListener;
import com.mongodb.client.model.Filters;
import com.sx4.bot.cache.GuildMessageCache;
import com.sx4.bot.database.Database;

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
			
			Document data = Database.get().getCommandLogs().find(Filters.eq("messageId", editedMessage.getIdLong())).first();
			if (data != null && ((editedMessage.isPinned() && !data.getBoolean("pinned")) || (!editedMessage.isPinned() && data.getBoolean("pinned")))) {
				return;
			}
			
			this.handle(editedMessage);
		}
	}
	
}
