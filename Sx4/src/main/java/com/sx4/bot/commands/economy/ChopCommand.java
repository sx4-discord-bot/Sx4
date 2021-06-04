package com.sx4.bot.commands.economy;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOptions;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.database.mongo.model.Operators;
import com.sx4.bot.entities.economy.item.*;
import com.sx4.bot.utility.EconomyUtility;
import com.sx4.bot.utility.ExceptionUtility;
import com.sx4.bot.utility.TimeUtility;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.List;
import java.util.stream.Collectors;

public class ChopCommand extends Sx4Command {

	public static final long COOLDOWN = 600L;

	public ChopCommand() {
		super("chop", 386);

		super.setDescription("Chop some trees down with your axe and collect some wood");
		super.setExamples("chop");
		super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
		super.setCategoryAll(ModuleCategory.ECONOMY);
	}

	public void onCommand(Sx4CommandEvent event) {
		EmbedBuilder embed = new EmbedBuilder();
		event.getMongo().withTransaction(session -> {
			Bson filter = Filters.and(
				Filters.eq("userId", event.getAuthor().getIdLong()),
				Filters.eq("item.type", ItemType.AXE.getId())
			);

			Document data = event.getMongo().getItems().find(session, filter).first();
			if (data == null) {
				event.replyFailure("You do not have a axe").queue();
				session.abortTransaction();
				return;
			}

			CooldownItemStack<Axe> axeStack = new CooldownItemStack<>(event.getBot().getEconomyManager(), data);

			long usableAmount = axeStack.getUsableAmount();
			if (usableAmount == 0) {
				event.reply("Slow down! You can chop some trees down again in " + TimeUtility.getTimeString(axeStack.getTimeRemaining()) + " :stopwatch:").queue();
				session.abortTransaction();
				return;
			}

			Axe axe = axeStack.getItem();

			List<ItemStack<Wood>> materialStacks = axe.getWoodYield();
			String materials = materialStacks.stream().map(ItemStack::toString).collect(Collectors.joining(", "));

			embed.setAuthor(event.getAuthor().getName(), null, event.getAuthor().getEffectiveAvatarUrl())
				.setColor(event.getMember().getColorRaw())
				.setDescription(String.format("You chopped down some trees :axe:\nWood found: %s", materialStacks.isEmpty() ? "Nothing" : materials));

			if (axe.getDurability() == 2) {
				embed.appendDescription("\n\nYour axe will break the next time you use it :warning:");
			} else if (axe.getDurability() == 1) {
				embed.appendDescription("\n\nYour axe broke in the process");
			}

			Bson itemFilter = Filters.and(
				Filters.eq("userId", event.getAuthor().getIdLong()),
				Filters.eq("item.id", axe.getId())
			);

			if (axe.getDurability() == 1) {
				event.getMongo().getItems().deleteOne(session, itemFilter);
			} else {
				List<Bson> update = List.of(
					EconomyUtility.getResetsUpdate(usableAmount, ChopCommand.COOLDOWN),
					Operators.set("item.durability", Operators.subtract("$item.durability", 1))
				);

				event.getMongo().getItems().updateOne(session, itemFilter, update);
			}

			for (ItemStack<?> stack : materialStacks) {
				Item item = stack.getItem();

				List<Bson> update = List.of(
					Operators.set("item", item.toData()),
					Operators.set("amount", Operators.add(Operators.ifNull("$amount", 0), stack.getAmount()))
				);

				Bson materialFilter = Filters.and(
					Filters.eq("userId", event.getAuthor().getIdLong()),
					Filters.eq("item.id", item.getId())
				);

				event.getMongo().getItems().updateOne(session, materialFilter, update, new UpdateOptions().upsert(true));
			}
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
