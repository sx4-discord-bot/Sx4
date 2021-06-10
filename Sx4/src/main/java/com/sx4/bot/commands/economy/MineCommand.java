package com.sx4.bot.commands.economy;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
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

public class MineCommand extends Sx4Command {

	public static final long COOLDOWN = 900L;

	public MineCommand() {
		super("mine", 358);

		super.setDescription("Mine every 15 minutes with your pickaxe to make some money and get some materials");
		super.setExamples("mine");
		super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
		super.setCategoryAll(ModuleCategory.ECONOMY);
	}

	public void onCommand(Sx4CommandEvent event) {
		EmbedBuilder embed = new EmbedBuilder();
		event.getMongo().withTransaction(session -> {
			Bson filter = Filters.and(
				Filters.eq("userId", event.getAuthor().getIdLong()),
				Filters.eq("item.type", ItemType.PICKAXE.getId())
			);

			Document data = event.getMongo().getItems().find(session, filter).first();
			if (data == null) {
				event.replyFailure("You do not have a pickaxe").queue();
				session.abortTransaction();
				return;
			}

			CooldownItemStack<Pickaxe> pickaxeStack = new CooldownItemStack<>(event.getBot().getEconomyManager(), data);

			long usableAmount = pickaxeStack.getUsableAmount();
			if (usableAmount == 0) {
				event.reply("Slow down! You can go mining again in " + TimeUtility.getTimeString(pickaxeStack.getTimeRemaining()) + " :stopwatch:").queue();
				session.abortTransaction();
				return;
			}

			Pickaxe pickaxe = pickaxeStack.getItem();

			long yield = pickaxe.getYield();

			List<ItemStack<Material>> materialStacks = pickaxe.getMaterialYield();
			String materials = materialStacks.stream().map(ItemStack::getItem).map(item -> item.getName() + item.getEmote()).collect(Collectors.joining(", "));

			embed.setAuthor(event.getAuthor().getName(), null, event.getAuthor().getEffectiveAvatarUrl())
				.setColor(event.getMember().getColorRaw())
				.setDescription(String.format("You mined resources and made **$%,d** :pick:\nMaterials found: %s", yield, materialStacks.isEmpty() ? "Nothing" : materials));

			if (pickaxe.getDurability() == 2) {
				embed.appendDescription("\n\nYour pickaxe will break the next time you use it :warning:");
			} else if (pickaxe.getDurability() == 1) {
				embed.appendDescription("\n\nYour pickaxe broke in the process");
			}

			Bson itemFilter = Filters.and(
				Filters.eq("userId", event.getAuthor().getIdLong()),
				Filters.eq("item.id", pickaxe.getId())
			);

			if (pickaxe.getDurability() == 1) {
				event.getMongo().getItems().deleteOne(session, itemFilter);
			} else {
				List<Bson> update = List.of(
					EconomyUtility.getResetsUpdate(usableAmount, MineCommand.COOLDOWN),
					Operators.set("item.durability", Operators.subtract("$item.durability", 1))
				);

				event.getMongo().getItems().updateOne(session, itemFilter, update);
			}

			for (ItemStack<?> stack : materialStacks) {
				Item item = stack.getItem();

				List<Bson> update = List.of(
					Operators.set("item", item.toData()),
					Operators.set("amount", Operators.add(Operators.ifNull("$amount", 0L), stack.getAmount()))
				);

				Bson materialFilter = Filters.and(
					Filters.eq("userId", event.getAuthor().getIdLong()),
					Filters.eq("item.id", item.getId())
				);

				event.getMongo().getItems().updateOne(session, materialFilter, update, new UpdateOptions().upsert(true));
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
