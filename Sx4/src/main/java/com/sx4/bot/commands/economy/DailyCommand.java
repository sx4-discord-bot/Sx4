package com.sx4.bot.commands.economy;

import com.mongodb.client.model.*;
import com.mongodb.client.result.UpdateResult;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.database.mongo.MongoDatabase;
import com.sx4.bot.database.mongo.model.Operators;
import com.sx4.bot.entities.economy.item.Crate;
import com.sx4.bot.utility.ExceptionUtility;
import com.sx4.bot.utility.TimeUtility;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class DailyCommand extends Sx4Command {

	public static final long COOLDOWN = 86400L;

	public DailyCommand() {
		super("daily", 407);

		super.setDescription("Collect your daily paycheck, repeatedly collect it everyday to up your streaks, the bigger the streak the better chance of getting a higher tier crate");
		super.setAliases("pd", "payday", "pay day");
		super.setExamples("daily");
		super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
		super.setCategoryAll(ModuleCategory.ECONOMY);
	}

	public void onCommand(Sx4CommandEvent event) {
		List<Bson> update = List.of(
			Operators.set("economy.balance", Operators.let(new Document("reset", Operators.ifNull("$economy.resets.daily", 0L)).append("balance", Operators.ifNull("$economy.balance", 0L)), Operators.let(new Document("streak", Operators.cond(Operators.and(Operators.gte(Operators.nowEpochSecond(), "$$reset"), Operators.lt(Operators.nowEpochSecond(), Operators.add("$$reset", DailyCommand.COOLDOWN))), Operators.add(Operators.ifNull("$economy.streak", 0), 1), 0)), Operators.cond(Operators.lt(Operators.nowEpochSecond(), "$$reset"), "$$balance", Operators.add("$$balance", Operators.add(100L, Operators.multiply(Operators.min(10L, "$$streak"), Operators.add(20L, Operators.multiply(Operators.min(10L, "$$streak"), 5L))))))))),
			Operators.set("economy.streak", Operators.let(new Document("reset", Operators.ifNull("$economy.resets.daily", 0L)).append("streak", Operators.ifNull("$economy.streak", 0)), Operators.cond(Operators.and(Operators.gte(Operators.nowEpochSecond(), "$$reset"), Operators.lt(Operators.nowEpochSecond(), Operators.add("$$reset", DailyCommand.COOLDOWN))), Operators.add("$$streak", 1), Operators.cond(Operators.lt(Operators.nowEpochSecond(), "$$reset"), "$$streak", 0)))),
			Operators.set("economy.resets.daily", Operators.let(new Document("reset", Operators.ifNull("$economy.resets.daily", 0L)), Operators.cond(Operators.lt(Operators.nowEpochSecond(), "$$reset"), "$$reset", Operators.add(Operators.nowEpochSecond(), DailyCommand.COOLDOWN))))
		);

		FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().returnDocument(ReturnDocument.BEFORE).projection(Projections.include("economy.resets.daily", "economy.streak")).upsert(true);

		EmbedBuilder embed = new EmbedBuilder()
			.setAuthor(event.getAuthor().getName(), null, event.getAuthor().getEffectiveAvatarUrl())
			.setColor(event.getMember().getColor());

		event.getMongo().findAndUpdateUserById(event.getAuthor().getIdLong(), update, options).thenCompose(data -> {
			Document economy = (data == null ? MongoDatabase.EMPTY_DOCUMENT : data).get("economy", MongoDatabase.EMPTY_DOCUMENT);

			long reset = economy.getEmbedded(List.of("resets", "daily"), 0L), timestamp = Clock.systemUTC().instant().getEpochSecond();
			if (timestamp < reset) {
				event.reply("Slow down! You can collect your daily in " + TimeUtility.getTimeString(reset - timestamp) + " :stopwatch:").queue();
				return CompletableFuture.completedFuture(null);
			}

			int previousStreak = economy.get("streak", 0);
			int streak = timestamp < reset + DailyCommand.COOLDOWN ? previousStreak + 1 : 0;
			long money = 100 + Math.min(10, streak) * (20 + Math.min(10, streak) * 5L);

			embed.setDescription("You have collected your daily money (**$" + money + "**)" + (streak == 0 && previousStreak != streak ? "\n\nIt has been over 2 days since you last collected your daily, your streak has been reset" : streak == 0 ? "" : "\nYou had a bonus of $" + (money - 100) + String.format(" for having a %,d day streak", streak)));

			List<Crate> crates = new ArrayList<>();
			if (streak != 0) {
				for (Crate crate : event.getBot().getEconomyManager().getItems(Crate.class)) {
					if (crate.isHidden()) {
						continue;
					}

					double randomDouble = event.getBot().getEconomyManager().getRandom().nextDouble();
					if (randomDouble <= Math.min(1D / Math.ceil((crate.getPrice() / 10D / streak) * 4), 1)) {
						crates.add(crate);
					}
				}
			}

			crates.sort(Comparator.comparingLong(Crate::getPrice).reversed());
			if (!crates.isEmpty()) {
				Crate crate = crates.get(0);
				embed.appendDescription(String.format("\n\nYou also received a `%s` (**$%,d**), it has been added to your items.", crate.getName(), crate.getPrice()));

				List<Bson> crateUpdate = List.of(
					Operators.set("item", crate.toData()),
					Operators.set("amount", Operators.add(Operators.ifNull("$amount", 0L), 1L))
				);

				return event.getMongo().updateItem(Filters.and(Filters.eq("userId", event.getAuthor().getIdLong()), Filters.eq("item.id", crate.getId())), crateUpdate, new UpdateOptions().upsert(true));
			}

			return CompletableFuture.completedFuture(UpdateResult.acknowledged(0L, 0L, null));
		}).whenComplete((result, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception) || result == null) {
				return;
			}

			event.reply(embed.build()).queue();
		});
	}

}
