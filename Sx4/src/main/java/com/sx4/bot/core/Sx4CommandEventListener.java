package com.sx4.bot.core;

import com.jockie.bot.core.command.ICommand;
import com.jockie.bot.core.command.impl.CommandEvent;
import com.jockie.bot.core.command.impl.CommandEventListener;
import com.jockie.bot.core.command.impl.DummyCommand;
import com.sx4.bot.database.Database;
import com.sx4.bot.utility.ExceptionUtility;
import org.bson.Document;

import java.util.Arrays;

public class Sx4CommandEventListener extends CommandEventListener {

	public void onCommandExecuted(ICommand command, CommandEvent event) {
		Sx4Command effectiveCommand = command instanceof DummyCommand ? (Sx4Command) ((DummyCommand) command).getActualCommand() : (Sx4Command) command;

		Document commandData = new Document("messageId", event.getMessage().getIdLong())
			.append("content", event.getMessage().getContentRaw())
			.append("command", new Document("name", command.getCommandTrigger()).append("id", effectiveCommand.getId()))
			.append("module", command.getCategory() == null ? null : command.getCategory().getName())
			.append("aliasUsed", event.getCommandTrigger())
			.append("authorId", event.getAuthor().getIdLong())
			.append("channelId", event.getChannel().getIdLong())
			.append("guildId", event.getGuild().getIdLong())
			.append("arguments", Arrays.asList(event.getRawArguments()))
			.append("prefix", event.getPrefix())
			.append("executionDuration", event.getTimeSinceStarted());

		Database.get().insertCommand(commandData).whenComplete(Database.exceptionally());
	}

	public void onCommandExecutionException(ICommand command, CommandEvent event, Throwable throwable) {
		ExceptionUtility.sendExceptionally(event, throwable);
	}

}
