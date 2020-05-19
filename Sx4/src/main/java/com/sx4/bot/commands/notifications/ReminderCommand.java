package com.sx4.bot.commands.notifications;

import com.jockie.bot.core.command.Command;
import com.sx4.bot.annotations.command.Redirects;
import com.sx4.bot.category.Category;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;

public class ReminderCommand extends Sx4Command {
	
	public ReminderCommand() {
		super("reminder");
		
		super.setDescription("Create reminders to keep up to date with tasks");
		super.setExamples("reminder add", "reminder remove", "reminder list");
		super.setCategory(Category.NOTIFICATIONS);
	}
	
	public void onCommand(Sx4CommandEvent event) {
		
	}
	
	@Command(value="list", description="Get a list of your current active reminders")
	@Redirects({"reminders"})
	public void list(Sx4CommandEvent event) {
		event.reply("testing").queue();
	}

}
