package com.sx4.bot.core;

import com.jockie.bot.core.command.ICommand;
import com.jockie.bot.core.command.impl.CommandEvent;
import com.jockie.bot.core.command.impl.CommandListener;
import com.sx4.bot.database.Database;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.message.MessageUpdateEvent;
import org.bson.Document;

import java.util.function.BiPredicate;
import java.util.function.Function;

public class Sx4CommandListener extends CommandListener {

	public Sx4CommandListener removePreExecuteCheck(Function<Sx4CommandListener, BiPredicate<CommandEvent, ICommand>> function) {
		super.removePreExecuteCheck(function.apply(this));

		return this;
	}

	public Sx4CommandListener addPreExecuteCheck(Function<Sx4CommandListener, BiPredicate<CommandEvent, ICommand>> function) {
		super.addPreExecuteCheck(function.apply(this));

		return this;
	}

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