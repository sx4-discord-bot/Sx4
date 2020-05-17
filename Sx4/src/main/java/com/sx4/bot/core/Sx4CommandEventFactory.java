package com.sx4.bot.core;

import java.util.Map;

import com.jockie.bot.core.command.ICommand;
import com.jockie.bot.core.command.ICommand.ArgumentParsingType;
import com.jockie.bot.core.command.factory.ICommandEventFactory;
import com.jockie.bot.core.command.impl.CommandEvent;
import com.jockie.bot.core.command.impl.CommandListener;

import net.dv8tion.jda.api.entities.Message;

public class Sx4CommandEventFactory implements ICommandEventFactory {

	@Override
	public CommandEvent create(Message message, CommandListener listener, ICommand command, Object[] arguments,
			String[] rawArguments, String prefix, String commandTrigger, Map<String, Object> options,
			ArgumentParsingType parsingType, String contentOverflow, long timeStarted) {
		return new Sx4CommandEvent(message, listener, command, arguments, rawArguments, prefix, commandTrigger, options, parsingType, contentOverflow, timeStarted);
	}
	
}
