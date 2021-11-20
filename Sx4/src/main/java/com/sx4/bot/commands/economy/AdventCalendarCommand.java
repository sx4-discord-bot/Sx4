package com.sx4.bot.commands.economy;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.database.mongo.model.Operators;
import com.sx4.bot.entities.economy.item.Crate;
import com.sx4.bot.entities.economy.item.Item;
import com.sx4.bot.entities.economy.item.Tool;
import com.sx4.bot.managers.EconomyManager;
import com.sx4.bot.utility.ExceptionUtility;
import com.sx4.bot.utility.NumberUtility;
import com.sx4.bot.utility.TimeUtility;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class AdventCalendarCommand extends Sx4Command {

	public static final int PRESENT_CRATE = 52;

	public AdventCalendarCommand() {
		super("advent calendar", 471);

		super.setDescription("Open your advent calendar to get a random item every day up to the 24th");
		super.setExamples("advent calendar");
		super.setAliases("adventcalendar", "advent", "calendar");
		super.setCategoryAll(ModuleCategory.ECONOMY);
	}

	public void onCommand(Sx4CommandEvent event) {
		ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
		int day = now.getDayOfMonth();
		if (now.getMonthValue() != 12 || day > 24) {
			event.replyFormat("There's no advent calendar box for the %s %s :no_entry:", NumberUtility.getSuffixed(day), now.getMonth().getDisplayName(TextStyle.FULL, Locale.UK)).queue();
			return;
		}

		EconomyManager manager = event.getBot().getEconomyManager();

		event.getMongo().withTransaction(session -> {
			Document data = event.getMongo().getUsers().findOneAndUpdate(session, Filters.eq("_id", event.getAuthor().getIdLong()), Updates.addToSet("economy.opened", day), new FindOneAndUpdateOptions().upsert(true));

			List<Integer> opened = data == null ? Collections.emptyList() : data.getEmbedded(List.of("economy", "opened"), Collections.emptyList());
			if (opened.contains(day)) {
				long secondsTillTomorrow = now.toLocalDate().atStartOfDay(ZoneOffset.UTC).plusDays(1).toEpochSecond() - now.toEpochSecond();
				event.replyFormat("You've already opened today's box on your advent calendar%s :no_entry:", day != 24 ? ", you can open tomorrows in **" + TimeUtility.LONG_TIME_FORMATTER.parse(Duration.of(secondsTillTomorrow, ChronoUnit.SECONDS)) + "**" : "").queue();

				session.abortTransaction();
				return null;
			}

			List<Item> items = manager.getItems();
			items.sort(Comparator.comparingLong(Item::getPrice).reversed());

			Item item = items.get(items.size() - 1);
			for (Item winnableItem : items) {
				if (winnableItem instanceof Tool) {
					continue;
				}

				int equation = (int) Math.ceil(winnableItem.getPrice() / Math.pow(day * 3, 2));
				if (manager.getRandom().nextInt(equation + 1) == 0) {
					item = winnableItem;
					break;
				}
			}

			if (opened.size() == 23) {
				Crate present = manager.getItemById(PRESENT_CRATE, Crate.class);

				List<Bson> update = List.of(
					Operators.set("item", present.toData()),
					Operators.set("amount", Operators.add(Operators.ifNull("$amount", 0L), 1L))
				);

				event.getMongo().getItems().updateOne(session, Filters.and(Filters.eq("userId", event.getAuthor().getIdLong()), Filters.eq("item.id", item.getId())), update, new UpdateOptions().upsert(true));
			}

			List<Bson> update = List.of(
				Operators.set("item", item.toData()),
				Operators.set("amount", Operators.add(Operators.ifNull("$amount", 0L), 1L))
			);

			event.getMongo().getItems().updateOne(session, Filters.and(Filters.eq("userId", event.getAuthor().getIdLong()), Filters.eq("item.id", item.getId())), update, new UpdateOptions().upsert(true));

			return "You opened your advent calendar for the " + NumberUtility.getSuffixed(day) + " and got **" + item.getName() + "**" + (opened.size() == 23 ? " and a **Present Crate**" : "") + " :christmas_tree:";
		}).whenComplete((content, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception) || content == null) {
				return;
			}

			event.reply(content).queue();
		});
	}

}
