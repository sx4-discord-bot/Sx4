package com.sx4.bot.core;

import java.util.Map;

import com.jockie.bot.core.command.ICommand;
import com.jockie.bot.core.command.ICommand.ArgumentParsingType;
import com.jockie.bot.core.command.impl.CommandEvent;
import com.jockie.bot.core.command.impl.CommandListener;
import com.sx4.bot.database.Database;

import net.dv8tion.jda.api.entities.Message;

public class Sx4CommandEvent extends CommandEvent {

	public Sx4CommandEvent(Message message, CommandListener listener, ICommand command, 
			Object[] arguments, String[] rawArguments, String prefix, String commandTrigger, 
			Map<String, Object> options, ArgumentParsingType parsingType, String contentOverflow, long timeStarted) {
		super(message, listener, command, arguments, rawArguments, prefix, commandTrigger, options, parsingType, contentOverflow, timeStarted);
	}
	
	public Sx4CommandEvent(CommandEvent event) {
		super(
			event.getMessage(), 
			event.getCommandListener(), 
			event.getCommand(), 
			event.getArguments(), 
			event.getRawArguments(), 
			event.getPrefix(), 
			event.getCommandTrigger(), 
			event.getOptions(), 
			event.getParsingType(), 
			event.getContentOverflow(), 
			event.getTimeStarted()
		);
	}
	
	public Sx4Command getCommand() {
		return (Sx4Command) this.command;
	}
	
	public Sx4CommandListener getCommandListener() {
		return (Sx4CommandListener) this.commandListener;
	}
	
	public Database getDatabase() {
		return Database.get();
	}
	
}
