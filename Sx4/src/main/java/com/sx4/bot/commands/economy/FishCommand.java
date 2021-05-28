package com.sx4.bot.commands.economy;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.database.mongo.model.Operators;
import com.sx4.bot.entities.economy.item.CooldownItemStack;
import com.sx4.bot.entities.economy.item.ItemType;
import com.sx4.bot.entities.economy.item.Rod;
import com.sx4.bot.utility.EconomyUtility;
import com.sx4.bot.utility.ExceptionUtility;
import com.sx4.bot.utility.TimeUtility;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.List;

public class FishCommand extends Sx4Command {

	public static final long COOLDOWN = 300L;

	public FishCommand() {
		super("fish", 355);

		super.setDescription("Fish every 5 minutes with your fishing rod and get some money in return");
		super.setExamples("fish");
		super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
		super.setCategoryAll(ModuleCategory.ECONOMY);
	}

	public void onCommand(Sx4CommandEvent event) {
		EmbedBuilder embed = new EmbedBuilder();

		event.getMongo().withTransaction(session -> {
			Bson filter = Filters.and(
				Filters.eq("userId", event.getAuthor().getIdLong()),
				Filters.eq("item.type", ItemType.ROD.getId())
			);

			Document data = event.getMongo().getItems().find(session, filter).first();
			if (data == null) {
				event.replyFailure("You do not have a fishing rod").queue();
				session.abortTransaction();
				return;
			}

			CooldownItemStack<Rod> rodStack = new CooldownItemStack<>(event.getBot().getEconomyManager(), data);

			long usableAmount = rodStack.getUsableAmount();
			if (usableAmount == 0) {
				event.reply("Slow down! You can go fishing again in " + TimeUtility.getTimeString(rodStack.getTimeRemaining()) + " :stopwatch:").queue();
				session.abortTransaction();
				return;
			}

			Rod rod = rodStack.getItem();
			long yield = rod.getYield(event.getBot().getEconomyManager());

			embed.setAuthor(event.getAuthor().getName(), null, event.getAuthor().getEffectiveAvatarUrl())
				.setColor(event.getMember().getColorRaw())
				.setDescription(String.format("You fish for 5 minutes and sell your fish! (**$%,d**) :fish:", yield));

			if (rod.getCurrentDurability() == 2) {
				embed.appendDescription("\n\nYour fishing rod will break the next time you use it :warning:");
			} else if (rod.getCurrentDurability() == 1) {
				embed.appendDescription("\n\nYour fishing rod broke in the process");
			}


			Bson itemFilter = Filters.and(
				Filters.eq("userId", event.getAuthor().getIdLong()),
				Filters.eq("item.id", rod.getId())
			);

			if (rod.getCurrentDurability() == 1) {
				event.getMongo().getItems().deleteOne(session, itemFilter);
			} else {
				List<Bson> update = List.of(
					EconomyUtility.getResetsUpdate(usableAmount, FishCommand.COOLDOWN),
					Operators.set("item.currentDurability", Operators.subtract("$item.currentDurability", 1))
				);

				 event.getMongo().getItems().updateOne(session, itemFilter, update);
			}

			event.getMongo().getUsers().updateOne(session, Filters.eq("_id", event.getAuthor().getIdLong()), Updates.inc("economy.balance", yield), new UpdateOptions().upsert(true));
		}).whenComplete((updated, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			if (updated) {
				event.reply(embed.build()).queue();
			}
		});
	}

}
