package com.sx4.bot.commands.economy;

import com.mongodb.client.model.*;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.database.model.Operators;
import com.sx4.bot.entities.economy.item.CooldownItemStack;
import com.sx4.bot.entities.economy.item.ItemType;
import com.sx4.bot.entities.economy.item.Rod;
import com.sx4.bot.utility.EconomyUtility;
import com.sx4.bot.utility.ExceptionUtility;
import com.sx4.bot.utility.TimeUtility;
import net.dv8tion.jda.api.EmbedBuilder;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class FishCommand extends Sx4Command {

	public static final long COOLDOWN = 300L;

	public FishCommand() {
		super("fish", 355);

		super.setDescription("Fish every 5 minutes and start making some money");
		super.setExamples("fish");
		super.setCategoryAll(ModuleCategory.ECONOMY);
	}

	public void onCommand(Sx4CommandEvent event) {
		Bson filter = Filters.and(
			Filters.eq("userId", event.getAuthor().getIdLong()),
			Filters.eq("type", ItemType.ROD.getType())
		);

		Document data = event.getDatabase().getItem(filter, Projections.include());
		if (data == null) {
			event.replyFailure("You do not have a fishing rod").queue();
			return;
		}

		CooldownItemStack<Rod> rodStack = new CooldownItemStack<>(event.getBot().getEconomyManager(), data);

		long usableAmount = rodStack.getUsableAmount();
		if (usableAmount == 0) {
			event.reply("Slow down! You can go fishing again in " + TimeUtility.getTimeString(rodStack.getTimeRemaining()) + " :stopwatch:").queue();
			return;
		}

		Rod rod = rodStack.getItem();
		long yield = rod.getYield(event.getRandom());

		EmbedBuilder embed = new EmbedBuilder()
			.setAuthor(event.getAuthor().getName(), null, event.getAuthor().getEffectiveAvatarUrl())
			.setColor(event.getMember().getColorRaw())
			.setDescription(String.format("You fish for 5 minutes and sell your fish! (**$%,d**) :fish:", yield));

		if (rod.getCurrentDurability() == 2) {
			embed.appendDescription("\n\nYour fishing rod will break the next time you use it :warning:");
		} else if (rod.getCurrentDurability() == 1) {
			embed.appendDescription("\n\nYour fishing rod broke in the process");
		}

		CompletableFuture<Void> future;
		if (rod.getCurrentDurability() == 1) {
			future = event.getDatabase().deleteItem(filter).thenApply($ -> null);
		} else {
			List<Bson> update = List.of(
				EconomyUtility.getResetsUpdate(usableAmount, FishCommand.COOLDOWN),
				Operators.set("item.currentDurability", Operators.subtract("$item.currentDurability", 1))
			);

			future = event.getDatabase().updateItem(filter, update, new UpdateOptions()).thenApply($ -> null);
		}

		future.thenCompose($ -> {
			return event.getDatabase().updateUserById(event.getAuthor().getIdLong(), Updates.inc("economy.balance", yield));
		}).whenComplete((result, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			event.reply(embed.build()).queue();
		});
	}

}
