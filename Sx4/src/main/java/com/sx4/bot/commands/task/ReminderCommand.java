package com.sx4.bot.commands.task;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.bson.types.ObjectId;

import com.jockie.bot.core.command.impl.CommandEvent;
import com.sun.tools.javac.util.List;
import com.sx4.bot.category.Category;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.paged.PagedResult;
import com.sx4.bot.utility.TimeUtility;

import net.dv8tion.jda.api.entities.Member;

public class ReminderCommand extends Sx4Command {
	
	private final Map<Long, Map<ObjectId, ScheduledFuture<?>>> executors = new HashMap<>();
	
	private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

	public ReminderCommand() {
		super("reminder");
		
		super.setDescription("Create reminders to keep up to date with tasks");
		super.setExamples("reminder add", "reminder remove", "reminder list");
		super.setCategory(Category.TASK);
	}
	
	public void onCommand(CommandEvent event) {
		event.reply(TimeUtility.getMusicTimeString(120L)).queue();
	}

}
