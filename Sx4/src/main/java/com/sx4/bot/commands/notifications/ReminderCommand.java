package com.sx4.bot.commands.notifications;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.command.Command;
import com.jockie.bot.core.option.Option;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Updates;
import com.sx4.bot.annotations.command.CommandId;
import com.sx4.bot.annotations.command.Examples;
import com.sx4.bot.annotations.command.Redirects;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.entities.argument.ReminderArgument;
import com.sx4.bot.paged.PagedResult;
import com.sx4.bot.utility.ExceptionUtility;
import com.sx4.bot.utility.StringUtility;
import com.sx4.bot.utility.TimeUtility;
import com.sx4.bot.utility.TimeUtility.OffsetTimeZone;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class ReminderCommand extends Sx4Command {
	
	public ReminderCommand() {
		super("reminder", 152);
		
		super.setDescription("Create reminders to keep up to date with tasks");
		super.setExamples("reminder add", "reminder remove", "reminder list");
		super.setCategoryAll(ModuleCategory.NOTIFICATIONS);
	}
	
	public void onCommand(Sx4CommandEvent event) {
		event.replyHelp().queue();
	}
	
	@Command(value="add", description="Create a reminder so the bot will message you when the time is up", argumentInfo="reminder add <reminder>* in <time>*\nreminder add <reminder>* at <date time>*")
	@CommandId(153)
	@Examples({"reminder add Football game in 4 hours", "reminder add Party at 21/07/20 15:00 UTC+1", "reminder add Finish coursework at 12:00", "reminder add fish in 5 minutes --repeat", "reminder add weekly task at 23/05 --repeat=7d"})
	public void add(Sx4CommandEvent event, @Argument(value="reminder", endless=true) ReminderArgument reminder, @Option(value="repeat", description="Continuosly repeats the reminder after the initial duration is up") Duration repeat) {
		long initialDuration = reminder.getDuration();
		boolean repeatOption = event.isOptionPresent("repeat");

		long duration = repeat == null ? initialDuration : repeat.toSeconds();
		if (duration < 30 && repeatOption) {
			event.replyFailure("Repeated reminders have to be at least 30 seconds long").queue();
			return;
		}
		
		if (reminder.getReminder().length() > 1500) {
			event.replyFailure("Your reminder cannot be longer than 1500 characters").queue();
			return;
		}

		Document data = new Document("userId", event.getAuthor().getIdLong())
			.append("repeat", repeatOption)
			.append("duration", duration)
			.append("reminder", reminder.getReminder())
			.append("remindAt", Clock.systemUTC().instant().getEpochSecond() + duration);

		event.getMongo().insertReminder(data).whenComplete((result, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			ObjectId id = result.getInsertedId().asObjectId().getValue();
			data.append("_id", id);

			event.getBot().getReminderManager().putReminder(initialDuration, data);

			event.replyFormat("I will remind you about that in **%s**, your reminder id is `%s` %s", TimeUtility.getTimeString(initialDuration), id.toHexString(), event.getConfig().getSuccessEmote()).queue();
		});
	}
	
	@Command(value="remove", aliases={"delete"}, description="Remove a reminder from being notified about")
	@CommandId(154)
	@Examples({"reminder remove 5ec67a3b414d8776950f0eee"})
	public void remove(Sx4CommandEvent event, @Argument(value="id", nullDefault=true) ObjectId id) {
		if (id == null) {
			List<Document> reminders = event.getMongo().getReminders(Filters.eq("userId", event.getAuthor().getIdLong()), Projections.include("reminder", "remindAt")).into(new ArrayList<>());
			if (reminders.isEmpty()) {
				event.replyFailure("You do not have any active reminders").queue();
				return;
			}

			long now = Clock.systemUTC().instant().getEpochSecond();

			PagedResult<Document> paged = new PagedResult<>(event.getBot(), reminders)
				.setAuthor("Reminders", null, event.getAuthor().getEffectiveAvatarUrl())
				.setPerPage(10)
				.setIndexed(true)
				.setDisplayFunction(data -> StringUtility.limit(data.getString("reminder"), 150) + " in `" + TimeUtility.getTimeString(data.getLong("remindAt") - now) + "`");

			paged.onSelect(select -> {
				ObjectId selected = select.getSelected().getObjectId("_id");
				event.getMongo().deleteReminderById(selected).whenComplete((result, exception) -> {
					if (ExceptionUtility.sendExceptionally(event, exception)) {
						return;
					}

					if (result.getDeletedCount() == 0) {
						event.replyFailure("You do not have a reminder with that id").queue();
						return;
					}

					event.getBot().getReminderManager().deleteExecutor(selected);

					event.replySuccess("You will no longer be reminded about that reminder").queue();
				});
			});

			paged.execute(event);
		} else {
			event.getMongo().deleteReminderById(id).whenComplete((result, exception) -> {
				if (ExceptionUtility.sendExceptionally(event, exception)) {
					return;
				}

				if (result.getDeletedCount() == 0) {
					event.replyFailure("You do not have a reminder with that id").queue();
					return;
				}

				event.getBot().getReminderManager().deleteExecutor(id);

				event.replySuccess("You will no longer be reminded about that reminder").queue();
			});
		}
	}
	
	@Command(value="list", description="Get a list of your current active reminders")
	@CommandId(155)
	@Examples({"reminder list"})
	@Redirects({"reminders"})
	public void list(Sx4CommandEvent event) {
		List<Document> reminders = event.getMongo().getReminders(Filters.eq("userId", event.getAuthor().getIdLong()), Projections.include("remindAt")).into(new ArrayList<>());
		if (reminders.isEmpty()) {
			event.replyFailure("You do not have any active reminders").queue();
			return;
		}

		long timeNow = Clock.systemUTC().instant().getEpochSecond();
		
		PagedResult<Document> paged = new PagedResult<>(event.getBot(), reminders)
			.setIndexed(false)
			.setAuthor(event.getAuthor().getName() + "'s Reminders", null, event.getAuthor().getEffectiveAvatarUrl())
			.setDisplayFunction(data -> data.getObjectId("_id").toHexString() + " - `" + TimeUtility.getTimeString(data.getLong("remindAt") - timeNow) + "`");
		
		paged.execute(event);
	}
	
	@Command(value="time zone", aliases={"zone"}, description="Set the default time zone to be used when specifiying a date when adding a reminder")
	@CommandId(156)
	@Examples({"reminder time zone UTC", "reminder time zone PST", "reminder time zone UTC+1"})
	public void timeZone(Sx4CommandEvent event, @Argument(value="time zone") OffsetTimeZone timeZone) {
		String zoneId = timeZone.toString();

		event.getMongo().updateUserById(event.getAuthor().getIdLong(), Updates.set("reminder.timeZone", zoneId)).whenComplete((result, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}
			
			if (result.getModifiedCount() == 0) {
				event.replyFailure("Your default time zone was already set to that").queue();
				return;
			}
			
			event.replyFormat("Your default time zone has been set to `%s` " + event.getConfig().getSuccessEmote(), zoneId).queue();
		});
	}

}
