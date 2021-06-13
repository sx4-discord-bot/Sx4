package com.sx4.bot.commands.fun;

import com.jockie.bot.core.option.Option;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.paged.PagedResult;
import com.sx4.bot.utility.NumberUtility;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import org.bson.Document;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.TextStyle;
import java.util.*;

public class BirthdaysCommand extends Sx4Command {

	public BirthdaysCommand() {
		super("birthdays", 286);

		super.setDescription("View upcoming birthdays for people who have set their birthday on Sx4");
		super.setExamples("birthdays", "birthdays --server");
		super.setExecuteAsync(true);
		super.setCooldownDuration(10);
		super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
		super.setCategoryAll(ModuleCategory.FUN);
	}

	public void onCommand(Sx4CommandEvent event, @Option(value="server", aliases={"guild"}, description="Only users in the current server can be shown") boolean guild) {
		List<Document> userData = event.getMongo().getUsers(Filters.exists("profile.birthday"), Projections.include("profile.birthday")).into(new ArrayList<>());

		LocalDate now = LocalDate.now(ZoneOffset.UTC);

		List<Map.Entry<User, LocalDate>> users = new ArrayList<>();
		for (Document data : userData) {
			User user;
			if (guild) {
				Member member = event.getGuild().getMemberById(data.getLong("_id"));
				user = member == null ? null : member.getUser();
			} else {
				user = event.getShardManager().getUserById(data.getLong("_id"));
			}

			if (user == null) {
				continue;
			}

			Document birthdayData = data.getEmbedded(List.of("profile", "birthday"), Document.class);

			int day = birthdayData.getInteger("day"), month = birthdayData.getInteger("month");
			if (month == 2 && day == 29 && !now.isLeapYear()) {
				continue;
			}

			LocalDate birthday = LocalDate.of(now.getYear(), month, day);
			if (birthday.compareTo(now) < 0) {
				birthday = birthday.plusYears(1);
			}

			long days = Duration.between(now.atStartOfDay(), birthday.atStartOfDay()).toDays();
			if (days >= 0 && days <= 30) {
				users.add(Map.entry(user, birthday));
			}
		}

		if (users.isEmpty()) {
			event.replyFailure("There are no upcoming birthdays").queue();
			return;
		}

		users.sort(Map.Entry.comparingByValue());

		PagedResult<Map.Entry<User, LocalDate>> paged = new PagedResult<>(event.getBot(), users)
			.setIndexed(false)
			.setAuthor("Upcoming Birthdays \uD83C\uDF82", null, null)
			.setPerPage(20)
			.setSelect()
			.setDisplayFunction(entry -> {
				LocalDate birthday = entry.getValue();
				return entry.getKey().getAsTag() + " - " + NumberUtility.getSuffixed(birthday.getDayOfMonth()) + " " + birthday.getMonth().getDisplayName(TextStyle.FULL, Locale.UK) + (birthday.equals(now) ? " :cake:" : "");
			});

		paged.execute(event);
	}

}
