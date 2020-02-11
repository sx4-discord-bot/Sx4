package com.sx4.bot.commands.task;

import com.jockie.bot.core.command.impl.CommandEvent;
import com.sx4.bot.category.Category;
import com.sx4.bot.core.Sx4Command;

public class ReminderCommand extends Sx4Command {
	
	public ReminderCommand() {
		super("reminder");
		
		super.setDescription("Create reminders to keep up to date with tasks");
		super.setExamples("reminder add", "reminder remove", "reminder list");
		super.setCategory(Category.TASK);
	}
	
	public String onCommand(CommandEvent event) {
		return "hi";
	}

}
