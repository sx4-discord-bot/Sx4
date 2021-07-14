package com.sx4.bot.commands.economy;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.command.Command;
import com.mongodb.client.model.*;
import com.mongodb.client.result.UpdateResult;
import com.sx4.bot.annotations.command.CommandId;
import com.sx4.bot.annotations.command.Examples;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.database.mongo.model.Operators;
import com.sx4.bot.entities.economy.item.*;
import com.sx4.bot.utility.EconomyUtility;
import com.sx4.bot.utility.ExceptionUtility;
import net.dv8tion.jda.api.EmbedBuilder;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.*;

public class ShopCommand extends Sx4Command {

	public ShopCommand() {
		super("shop", 452);

		super.setDescription("Buy and sell items to and from the bot");
		super.setExamples("shop buy", "shop sell", "shop list");
		super.setCategoryAll(ModuleCategory.ECONOMY);
	}

	public void onCommand(Sx4CommandEvent event) {
		event.replyHelp().queue();
	}

	@Command(value="buy", description="Buy items from the bot at 120% their original value")
	@CommandId(453)
	@Examples({"shop buy Coal", "shop buy 10 Oak", "shop buy Diamond Factory"})
	public void buy(Sx4CommandEvent event, @Argument(value="item", endless=true) ItemStack<Item> itemStack) {
		Item item = itemStack.getItem();
		long amount = itemStack.getAmount(), price = (long) Math.ceil(itemStack.getTotalPrice() * 1.2D);
		event.getMongo().withTransaction(session -> {
			UpdateResult balanceResult = event.getMongo().getUsers().updateOne(session, Filters.eq("_id", event.getAuthor().getIdLong()), List.of(EconomyUtility.decreaseBalanceUpdate(price)));
			if (balanceResult.getModifiedCount() == 0) {
				event.replyFormat("You do not have **$%,d** %s", price, event.getConfig().getFailureEmote()).queue();
				session.abortTransaction();
				return;
			}

			List<Bson> update = List.of(Operators.set("amount", Operators.let(new Document("amount", Operators.ifNull("$amount", 0L)), Operators.cond(Operators.lt("$$amount", amount), "$$amount", Operators.subtract("$$amount", amount)))));

			UpdateResult itemResult = event.getMongo().getItems().updateOne(session, Filters.and(Filters.eq("userId", event.getSelfUser().getIdLong()), Filters.eq("item.id", item.getId())), update);
			if (itemResult.getModifiedCount() == 0) {
				event.replyFormat("I do not have `%,d %s` %s", amount, item.getName(), event.getConfig().getFailureEmote()).queue();
				session.abortTransaction();
				return;
			}

			List<Bson> itemUpdate = List.of(
				Operators.set("item", item.toData()),
				Operators.set("amount", Operators.add(Operators.ifNull("$amount", 0L), amount))
			);

			event.getMongo().getItems().updateOne(session, Filters.and(Filters.eq("userId", event.getAuthor().getIdLong()), Filters.eq("item.id", item.getId())), itemUpdate, new UpdateOptions().upsert(true));
		}).whenComplete((updated, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception) || !updated) {
				return;
			}

			event.replyFormat("You just bought `%,d %s` for **$%,d** %s", amount, item.getName(), price, event.getConfig().getSuccessEmote()).queue();
		});
	}

	@Command(value="sell", description="Sell items to the bot for 80% their original value")
	@CommandId(454)
	@Examples({"shop sell Coal", "shop sell 10 Oak", "shop sell Diamond Factory"})
	public void sell(Sx4CommandEvent event, @Argument(value="item", endless=true) ItemStack<Item> itemStack) {
		Item item = itemStack.getItem();
		if (item instanceof Tool) {
			event.replyFailure("You can not sell tools").queue();
			return;
		}

		long amount = itemStack.getAmount(), price = (long) Math.floor(itemStack.getTotalPrice() * 0.8D);
		event.getMongo().withTransaction(session -> {
			List<Bson> update = List.of(Operators.set("amount", Operators.let(new Document("amount", Operators.ifNull("$amount", 0L)), Operators.cond(Operators.lt(Operators.subtract("$$amount", Operators.sum(Operators.map(Operators.filter(Operators.ifNull("$resets", Collections.EMPTY_LIST), Operators.gt("$$this.time", Operators.nowEpochSecond())), "$$this.amount"))), amount), "$$amount", Operators.subtract("$$amount", amount)))));
			FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().returnDocument(ReturnDocument.BEFORE).projection(Projections.include("amount", "resets"));

			Document data = event.getMongo().getItems().findOneAndUpdate(session, Filters.and(Filters.eq("userId", event.getAuthor().getIdLong()), Filters.eq("item.id", item.getId())), update, options);

			long authorAmount = data == null ? 0L : data.get("amount", 0L);
			if (authorAmount < amount) {
				event.replyFailure("You do not have `" + amount + " " + item.getName() + "`").queue();
				session.abortTransaction();
				return;
			}

			CooldownItemStack<Item> cooldownStack = new CooldownItemStack<>(item, data);

			long cooldownAmount = cooldownStack.getCooldownAmount();
			if (authorAmount - cooldownAmount < amount) {
				event.replyFormat("You have `%,d %s` but **%,d** %s on cooldown %s", authorAmount, item.getName(), cooldownAmount, cooldownAmount == 1 ? "is" : "are", event.getConfig().getFailureEmote()).queue();
				session.abortTransaction();
				return;
			}

			List<Bson> itemUpdate = List.of(
				Operators.set("item", item.toData()),
				Operators.set("amount", Operators.add(Operators.ifNull("$amount", 0L), amount))
			);

			event.getMongo().getItems().updateOne(session, Filters.and(Filters.eq("userId", event.getSelfUser().getIdLong()), Filters.eq("item.id", item.getId())), itemUpdate, new UpdateOptions().upsert(true));
			event.getMongo().getUsers().updateOne(session, Filters.eq("_id", event.getAuthor().getIdLong()), Updates.inc("economy.balance", price), new UpdateOptions().upsert(true));
		}).whenComplete((updated, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception) || !updated) {
				return;
			}

			event.replyFormat("You just sold `%,d %s` for **$%,d** %s", amount, item.getName(), price, event.getConfig().getSuccessEmote()).queue();
		});
	}

	@Command(value="list", description="View what items the bot has")
	@CommandId(455)
	@Examples({"shop list"})
	public void list(Sx4CommandEvent event) {
		List<Bson> pipeline = List.of(
			Aggregates.match(Filters.and(Filters.eq("userId", event.getSelfUser().getIdLong()), Filters.ne("amount", 0))),
			Aggregates.project(Projections.fields(Projections.computed("name", "$item.name"), Projections.computed("type", "$item.type"), Projections.include("item", "amount"))),
			Aggregates.sort(Sorts.descending("amount"))
		);

		event.getMongo().aggregateItems(pipeline).whenComplete((items, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			EmbedBuilder embed = new EmbedBuilder()
				.setAuthor("Shop List", null, event.getSelfUser().getEffectiveAvatarUrl())
				.setColor(event.getSelfMember().getColorRaw());

			if (items.isEmpty()) {
				event.replyFailure("That user does not have any items").queue();
				return;
			}

			Map<ItemType, StringJoiner> types = new HashMap<>();
			for (Document item : items) {
				ItemType type = ItemType.fromId(item.getInteger("type"));
				ItemStack<?> stack = new ItemStack<>(event.getBot().getEconomyManager(), item);

				types.compute(type, (key, value) -> (value == null ? new StringJoiner("\n") : value).add(stack.toString()));
			}

			types.forEach((type, joiner) -> embed.addField(type.getName(), joiner.toString(), true));

			event.reply(embed.build()).queue();
		});
	}

}
