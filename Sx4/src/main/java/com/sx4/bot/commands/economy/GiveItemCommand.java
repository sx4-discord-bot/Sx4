package com.sx4.bot.commands.economy;

import com.jockie.bot.core.argument.Argument;
import com.mongodb.client.model.*;
import com.mongodb.client.result.UpdateResult;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.database.mongo.model.Operators;
import com.sx4.bot.entities.economy.item.CooldownItemStack;
import com.sx4.bot.entities.economy.item.Item;
import com.sx4.bot.entities.economy.item.ItemStack;
import com.sx4.bot.entities.economy.item.Tool;
import com.sx4.bot.utility.EconomyUtility;
import com.sx4.bot.utility.ExceptionUtility;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.Collections;
import java.util.List;

public class GiveItemCommand extends Sx4Command {

	public GiveItemCommand() {
		super("give item", 400);

		super.setDescription("Give an item to another user");
		super.setAliases("give materials", "give mats", "givemats", "givematerials", "giveitem", "give items", "giveitems");
		super.setExamples("give item @Shea#6653 5 Coal", "give item Shea Diamond");
		super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
		super.setCategoryAll(ModuleCategory.ECONOMY);
	}

	public void onCommand(Sx4CommandEvent event, @Argument(value="user") Member member, @Argument(value="item", endless=true) ItemStack<Item> stack) {
		User user = member.getUser();
		if (user.isBot()) {
			event.replyFailure("You can not give items to bots").queue();
			return;
		}

		long amount = stack.getAmount();
		if (amount < 1) {
			event.replyFailure("You need to give at least 1 item").queue();
			return;
		}

		Item item = stack.getItem();
		if (item instanceof Tool) {
			event.replyFailure("You cannot give tools").queue();
			return;
		}

		long price = stack.getTotalPrice();
		long tax = (long) Math.ceil(price * 0.05D);

		event.getMongo().withTransaction(session -> {
			UpdateResult balanceResult = event.getMongo().getUsers().updateOne(session, Filters.eq("_id", event.getAuthor().getIdLong()), List.of(EconomyUtility.decreaseBalanceUpdate(tax)));
			if (balanceResult.getModifiedCount() == 0) {
				event.replyFormat("You do not have enough to pay the tax for this item (**$%,d**) %s", tax, event.getConfig().getFailureEmote()).queue();
				session.abortTransaction();
				return null;
			}

			FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().returnDocument(ReturnDocument.BEFORE).projection(Projections.include("amount", "resets"));

			Bson authorFilter = Filters.and(
				Filters.eq("userId", event.getAuthor().getIdLong()),
				Filters.eq("item.id", item.getId())
			);


			List<Bson> authorUpdate = List.of(Operators.set("amount", Operators.let(new Document("amount", Operators.ifNull("$amount", 0L)), Operators.cond(Operators.lt(Operators.subtract("$$amount", Operators.sum(Operators.map(Operators.filter(Operators.ifNull("$resets", Collections.EMPTY_LIST), Operators.gt("$$this.time", Operators.nowEpochSecond())), "$$this.amount"))), amount), "$$amount", Operators.subtract("$$amount", stack.getAmount())))));

			Document authorData = event.getMongo().getItems().findOneAndUpdate(session, authorFilter, authorUpdate, options);

			long authorAmount = authorData == null ? 0L : authorData.get("amount", 0L);
			if (authorAmount < amount) {
				event.replyFailure("You do not have `" + amount + " " + item.getName() + "`").queue();
				session.abortTransaction();
				return null;
			}

			CooldownItemStack<Item> cooldownStack = new CooldownItemStack<>(item, authorData);

			long cooldownAmount = cooldownStack.getCooldownAmount();
			if (authorAmount - cooldownAmount < amount) {
				event.replyFormat("You have `%,d %s` but **%,d** are on cooldown %s", authorAmount, item.getName(), cooldownAmount, event.getConfig().getFailureEmote()).queue();
				session.abortTransaction();
				return null;
			}

			Bson userFilter = Filters.and(
				Filters.eq("userId", member.getIdLong()),
				Filters.eq("item.id", item.getId())
			);

			List<Bson> userUpdate = List.of(
				Operators.set("item", item.toData()),
				Operators.set("amount", Operators.add(Operators.ifNull("$amount", 0), stack.getAmount()))
			);

			Document userData = event.getMongo().getItems().findOneAndUpdate(session, userFilter, userUpdate, options.upsert(true));

			event.getMongo().getUsers().updateOne(session, Filters.eq("_id", event.getSelfUser().getIdLong()), Updates.inc("economy.balance", tax));

			EmbedBuilder embed = new EmbedBuilder()
				.setColor(event.getMember().getColor())
				.setAuthor(event.getAuthor().getName() + " → " + member.getUser().getName(), null, "https://cdn0.iconfinder.com/data/icons/social-messaging-ui-color-shapes/128/money-circle-green-3-512.png")
				.setDescription(String.format("You have gifted **%,d %s** to **%s**\n\n%s's new %s amount: **%,d %s**\n%s's new %s amount: **%,d %s**", amount, item.getName(), user.getName(), event.getAuthor().getName(), item.getName(), authorAmount - amount, item.getName(), user.getName(), item.getName(), (userData == null ? 0L : userData.getLong("amount")) + amount, item.getName()))
				.setFooter(String.format("$%,d (%d%%) tax was taken", tax, Math.round((double) tax / price * 100)), null);

			return embed.build();
		}).whenComplete((embed, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception) || embed == null) {
				return;
			}

			event.reply(embed).queue();
		});
	}

}
