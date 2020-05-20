package com.sx4.bot.commands.notifications;

import java.util.Collections;
import java.util.List;
import java.util.TimeZone;

import org.bson.Document;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.command.Command;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Updates;
import com.sx4.bot.annotations.command.Examples;
import com.sx4.bot.annotations.command.Redirects;
import com.sx4.bot.category.Category;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.entities.reminder.ReminderArgument;
import com.sx4.bot.paged.PagedResult;
import com.sx4.bot.utility.ExceptionUtility;
import com.sx4.bot.utility.HelpUtility;

import net.dv8tion.jda.api.Permission;

public class ReminderCommand extends Sx4Command {
	
	public ReminderCommand() {
		super("reminder");
		
		super.setDescription("Create reminders to keep up to date with tasks");
		super.setExamples("reminder add", "reminder remove", "reminder list");
		super.setCategory(Category.NOTIFICATIONS);
	}
	
	public void onCommand(Sx4CommandEvent event) {
		event.reply(HelpUtility.getHelpMessage(event.getCommand(), event.getSelfMember().hasPermission(Permission.MESSAGE_EMBED_LINKS))).queue();
	}
	
	@Command(value="add", description="Create a reminder so the bot will message you when the time is up", argumentInfo="reminder add <reminder>* in <time>*\nreminder add <reminder>* at <date time>*")
	@Examples({"reminder add Football game in 4 hours", "reminder add Party at 21/07/20 15:00 UTC+1", "reminder add Finish coursework at 12:00", "reminder add fish in 5 minutes --repeat"})
	public void add(Sx4CommandEvent event, @Argument(value="reminder", endless=true) ReminderArgument reminder) {
		
	}
	
	@Command(value="list", description="Get a list of your current active reminders")
	@Examples({"reminder list"})
	@Redirects({"reminders"})
	public void list(Sx4CommandEvent event) {
		List<Document> reminders = this.database.getUserById(event.getAuthor().getIdLong(), Projections.include("reminder.reminders")).getEmbedded(List.of("reminder", "reminders"), Collections.emptyList());
		if (reminders.isEmpty()) {
			event.reply("You do not have any active reminder :no_entry:").queue();;
			return;
		}
		
		PagedResult<Document> paged = new PagedResult<>(reminders)
			.setIndexed(false)
			.setAuthor(event.getAuthor().getName() + "'s Reminders", null, event.getAuthor().getEffectiveAvatarUrl());
		
		paged.execute(event);
	}
	
	@Command(value="time zone", aliases={"zone"}, description="Set the default time zone to be used when specifiying a date when adding a reminder")
	@Examples({"reminder time zone utc", "reminder time zone pst", "reminder time zone utc+1"})
	public void timeZone(Sx4CommandEvent event, @Argument(value="time zone") TimeZone timeZone) {
		String zoneId = timeZone.getID();
		
		this.database.updateUserById(event.getAuthor().getIdLong(), Updates.set("reminder.timeZone", zoneId)).whenComplete((result, exception) -> {
			if (exception != null) {
				ExceptionUtility.sendExceptionally(event, exception);
			} else {
				if (result.getModifiedCount() == 0) {
					event.reply("Your default time zone was already set to that :no_entry:").queue();
					return;
				}
				
				event.replyFormat("Your default time zone has been set to `%s` <:done:403285928233402378>", zoneId).queue();
			}
		});
	}

}
