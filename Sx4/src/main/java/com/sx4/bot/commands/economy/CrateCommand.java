package com.sx4.bot.commands.economy;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.command.Command;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.result.UpdateResult;
import com.sx4.bot.annotations.command.BotPermissions;
import com.sx4.bot.annotations.command.CommandId;
import com.sx4.bot.annotations.command.Examples;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.database.mongo.model.Operators;
import com.sx4.bot.entities.economy.item.Crate;
import com.sx4.bot.entities.economy.item.Item;
import com.sx4.bot.entities.economy.item.ItemStack;
import com.sx4.bot.paged.MessagePagedResult;
import com.sx4.bot.paged.PagedResult;
import com.sx4.bot.utility.EconomyUtility;
import com.sx4.bot.utility.ExceptionUtility;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.StringJoiner;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class CrateCommand extends Sx4Command {

	public CrateCommand() {
		super("crate", 410);

		super.setDescription("Open crates to get random items in the economy");
		super.setExamples("crate shop", "crate buy", "crate open");
		super.setCategoryAll(ModuleCategory.ECONOMY);
	}

	public void onCommand(Sx4CommandEvent event) {
		event.replyHelp().queue();
	}

	@Command(value="shop", description="View all the crates you can buy")
	@CommandId(411)
	@Examples({"crate shop"})
	@BotPermissions(permissions={Permission.MESSAGE_EMBED_LINKS})
	public void shop(Sx4CommandEvent event) {
		List<Crate> crates = event.getBot().getEconomyManager().getItems(Crate.class)
			.stream()
			.filter(Predicate.not(Crate::isHidden))
			.collect(Collectors.toList());

		MessagePagedResult<Crate> paged = new MessagePagedResult.Builder<>(event.getBot(), crates)
			.setPerPage(12)
			.setSelect()
			.setCustomFunction(page -> {
				EmbedBuilder embed = new EmbedBuilder()
					.setDescription("Crates give you a random item, the better the crate the better the chance of a better item")
					.setAuthor("Crate Shop", null, event.getSelfUser().getEffectiveAvatarUrl())
					.setTitle("Page " + page.getPage() + "/" + page.getMaxPage())
					.setFooter(PagedResult.DEFAULT_FOOTER_TEXT);

				page.forEach((crate, index) -> embed.addField(crate.getName(), String.format("Price: $%,d\nContents: %s", crate.getPrice(), crate.getContentString()), true));

				return new MessageCreateBuilder().setEmbeds(embed.build());
			}).build();

		paged.execute(event);
	}

	@Command(value="buy", description="Buy a crate from the `crate shop`")
	@CommandId(412)
	@Examples({"crate buy 2 Shoe Crate", "crate buy Shoe Crate", "crate buy 5 Shoe"})
	public void buy(Sx4CommandEvent event, @Argument(value="crates", endless=true) ItemStack<Crate> stack) {
		long amount = stack.getAmount();
		if (amount < 1) {
			event.replyFailure("You need to buy at least 1 crate").queue();
			return;
		}

		long price = stack.getTotalPrice();

		Crate crate = stack.getItem();
		if (crate.isHidden()) {
			event.replyFailure("You cannot buy that crate").queue();
			return;
		}

		event.getMongo().withTransaction(session -> {
			UpdateResult result = event.getMongo().getUsers().updateOne(session, Filters.eq("_id", event.getAuthor().getIdLong()), List.of(EconomyUtility.decreaseBalanceUpdate(price)));
			if (result.getModifiedCount() == 0) {
				event.replyFormat("You do not have **$%,d** %s", price, event.getConfig().getFailureEmote()).queue();
				session.abortTransaction();
				return;
			}

			Bson filter = Filters.and(
				Filters.eq("userId", event.getAuthor().getIdLong()),
				Filters.eq("item.id", crate.getId())
			);

			List<Bson> update = List.of(
				Operators.set("item", crate.toData()),
				Operators.set("amount", Operators.add(Operators.ifNull("$amount", 0L), amount))
			);

			event.getMongo().getItems().updateOne(session, filter, update, new UpdateOptions().upsert(true));
		}).whenComplete((updated, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception) || !updated) {
				return;
			}

			event.replyFormat("You just bought `%,d %s` for **$%,d** %s", amount, crate.getName(), price, event.getConfig().getSuccessEmote()).queue();
		});
	}

	@Command(value="open", description="Open a crate you own")
	@CommandId(413)
	@Examples({"crate open Shoe Crate", "crate open Diamond Crate", "crate open Diamond"})
	@BotPermissions(permissions={Permission.MESSAGE_EMBED_LINKS})
	public void open(Sx4CommandEvent event, @Argument(value="crate", endless=true) Crate crate) {
		event.getMongo().withTransaction(session -> {
			List<Bson> removeUpdate = List.of(Operators.set("amount", Operators.let(new Document("amount", Operators.ifNull("$amount", 0L)), Operators.cond(Operators.lt("$$amount", 1), "$$amount", Operators.subtract("$$amount",1 )))));

			UpdateResult result = event.getMongo().getItems().updateOne(session, Filters.and(Filters.eq("userId", event.getAuthor().getIdLong()), Filters.eq("item.id", crate.getId())), removeUpdate);
			if (result.getModifiedCount() == 0) {
				event.replyFailure("You do not have a " + crate.getName()).queue();
				session.abortTransaction();
				return null;
			}

			EmbedBuilder embed = new EmbedBuilder()
				.setAuthor(event.getAuthor().getName(), null, event.getAuthor().getEffectiveAvatarUrl())
				.setColor(event.getMember().getColorRaw());

			List<ItemStack<?>> stacks = crate.open();
			if (stacks.isEmpty()) {
				embed.setDescription("You opened a `" + crate.getName() + "` and got scammed there was nothing in the crate");
				return embed;
			}

			stacks.sort(Collections.reverseOrder(Comparator.comparingLong(ItemStack::getAmount)));

			StringJoiner itemContent = new StringJoiner("\n");
			int totalCount = 0;
			for (ItemStack<?> stack : stacks) {
				Item item = stack.getItem();
				long amount = stack.getAmount();

				List<Bson> addUpdate = List.of(
					Operators.set("item", item.toData()),
					Operators.set("amount", Operators.add(Operators.ifNull("$amount", 0L), amount))
				);

				event.getMongo().getItems().updateOne(session, Filters.and(Filters.eq("userId", event.getAuthor().getIdLong()), Filters.eq("item.id", item.getId())), addUpdate, new UpdateOptions().upsert(true));

				itemContent.add("• " + amount + " " + item.getName());
				totalCount += amount;
			}

			embed.setDescription("You opened a `" + crate.getName() + "` and got **" + totalCount + "** item" + (totalCount == 1 ? "" : "s") + "\n\n" + itemContent);

			return embed;
		}).whenComplete((embed, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception) || embed == null) {
				return;
			}

			event.reply(embed.build()).queue();
		});
	}

}
