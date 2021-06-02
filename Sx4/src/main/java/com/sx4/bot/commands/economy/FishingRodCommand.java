package com.sx4.bot.commands.economy;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.command.Command;
import com.mongodb.client.model.*;
import com.mongodb.client.result.UpdateResult;
import com.sx4.bot.annotations.argument.Options;
import com.sx4.bot.annotations.command.BotPermissions;
import com.sx4.bot.annotations.command.CommandId;
import com.sx4.bot.annotations.command.Examples;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.database.mongo.model.Operators;
import com.sx4.bot.entities.argument.Alternative;
import com.sx4.bot.entities.economy.item.CraftItem;
import com.sx4.bot.entities.economy.item.ItemStack;
import com.sx4.bot.entities.economy.item.ItemType;
import com.sx4.bot.entities.economy.item.Rod;
import com.sx4.bot.paged.PagedResult;
import com.sx4.bot.utility.EconomyUtility;
import com.sx4.bot.utility.ExceptionUtility;
import com.sx4.bot.waiter.Waiter;
import com.sx4.bot.waiter.exception.CancelException;
import com.sx4.bot.waiter.exception.TimeoutException;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.interactions.components.Button;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;

public class FishingRodCommand extends Sx4Command {

	public FishingRodCommand() {
		super("fishing rod", 378);

		super.setDescription("View everything about fishing rods");
		super.setAliases("rod");
		super.setExamples("fishing rod shop", "fishing rod buy", "fishing rod info");
		super.setCategoryAll(ModuleCategory.ECONOMY);
	}

	public void onCommand(Sx4CommandEvent event) {
		event.replyHelp().queue();
	}

	@Command(value="shop", description="View all the fishing rods you are able to buy or craft")
	@CommandId(379)
	@Examples({"fishing rod shop"})
	@BotPermissions(permissions={Permission.MESSAGE_EMBED_LINKS})
	public void shop(Sx4CommandEvent event) {
		List<Rod> rods = event.getBot().getEconomyManager().getItems(Rod.class);

		PagedResult<Rod> paged = new PagedResult<>(event.getBot(), rods)
			.setPerPage(12)
			.setCustomFunction(page -> {
				EmbedBuilder embed = new EmbedBuilder()
					.setAuthor("Fishing Rod Shop", null, event.getSelfUser().getEffectiveAvatarUrl())
					.setTitle("Page " + page.getPage() + "/" + page.getMaxPage())
					.setDescription("Fishing rods are a good way to gain some money")
					.setFooter(PagedResult.DEFAULT_FOOTER_TEXT);

				page.forEach((rod, index) -> {
					List<ItemStack<CraftItem>> items = rod.getCraft();
					String craft = items.isEmpty() ? "None" : items.stream().map(ItemStack::toString).collect(Collectors.joining("\n"));
					embed.addField(rod.getName(), String.format("Price: $%,d\nCraft: %s\nDurability: %,d", rod.getPrice(), craft, rod.getMaxDurability()), true);
				});

				return new MessageBuilder().setEmbed(embed.build()).build();
			});

		paged.execute(event);
	}

	@Command(value="buy", description="Buy a fishing rod from the `fishing rod shop`")
	@CommandId(380)
	@Examples({"fishing rod buy Copper Rod", "fishing rod buy Copper"})
	public void buy(Sx4CommandEvent event, @Argument(value="fishing rod", endless=true) Rod rod) {
		event.getMongo().withTransaction(session -> {
			Bson filter = Filters.and(
				Filters.eq("userId", event.getAuthor().getIdLong()),
				Filters.eq("item.type", ItemType.ROD.getId())
			);

			Document rodData = event.getMongo().getItems().find(session, filter).projection(Projections.include("_id")).first();
			if (rodData != null) {
				event.replyFailure("You already own a fishing rod").queue();
				session.abortTransaction();
				return;
			}

			FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().returnDocument(ReturnDocument.BEFORE).projection(Projections.include("economy.balance")).upsert(true);

			Document data = event.getMongo().getUsers().findOneAndUpdate(session, Filters.eq("_id", event.getAuthor().getIdLong()), List.of(EconomyUtility.decreaseBalanceUpdate(rod.getPrice())), options);
			if (data == null || data.getEmbedded(List.of("economy", "balance"), 0L) < rod.getPrice()) {
				event.replyFailure("You cannot afford a **" + rod.getName() + "**").queue();
				session.abortTransaction();
				return;
			}

			Document insertData = new Document("userId", event.getAuthor().getIdLong())
				.append("amount", 1L)
				.append("item", rod.toData());

			event.getMongo().insertItem(insertData);
		}).whenComplete((updated, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			if (updated) {
				event.replySuccess("You just bought a **" + rod.getName() + "**").queue();
			}
		});
	}

	@Command(value="craft", description="Craft a fishing rod from the `fishing rod shop`")
	@CommandId(381)
	@Examples({"fishing rod craft Copper Rod", "fishing rod craft Copper"})
	public void craft(Sx4CommandEvent event, @Argument(value="fishing rod", endless=true) Rod rod) {
		event.getMongo().withTransaction(session -> {
			Bson filter = Filters.and(
				Filters.eq("userId", event.getAuthor().getIdLong()),
				Filters.eq("item.type", ItemType.ROD.getId())
			);

			Document rodData = event.getMongo().getItems().find(session, filter).projection(Projections.include("_id")).first();
			if (rodData != null) {
				event.replyFailure("You already own a fishing rod").queue();
				session.abortTransaction();
				return;
			}

			List<ItemStack<CraftItem>> craft = rod.getCraft();
			if (craft.isEmpty()) {
				event.replyFailure("You cannot craft this fishing rod").queue();
				session.abortTransaction();
				return;
			}

			for (ItemStack<CraftItem> stack : craft) {
				CraftItem item = stack.getItem();

				Bson itemFilter = Filters.and(
					Filters.eq("userId", event.getAuthor().getIdLong()),
					Filters.eq("item.id", item.getId())
				);

				List<Bson> update = List.of(Operators.set("amount", Operators.let(new Document("amount", Operators.ifNull("$amount", 0)), Operators.cond(Operators.lte(stack.getAmount(), "$$amount"), Operators.subtract("$$amount", stack.getAmount()), "$$amount"))));

				UpdateResult result = event.getMongo().getItems().updateOne(session, itemFilter, update);
				if (result.getModifiedCount() == 0) {
					event.replyFailure("You do not have `" + stack.getAmount() + " " + item.getName() + "`").queue();
					session.abortTransaction();
					return;
				}
			}

			Document insertData = new Document("userId", event.getAuthor().getIdLong())
				.append("amount", 1L)
				.append("item", rod.toData());

			event.getMongo().getItems().insertOne(session, insertData);
		}).whenComplete((updated, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			if (updated) {
				event.replySuccess("You just crafted a **" + rod.getName() + "**").queue();
			}
		});
	}

	@Command(value="info", aliases={"information"}, description="View information on a users fishing rod")
	@CommandId(382)
	@Examples({"fishing rod info", "fishing rod info @Shea#6653", "fishing rod info Shea"})
	@BotPermissions(permissions={Permission.MESSAGE_EMBED_LINKS})
	public void info(Sx4CommandEvent event, @Argument(value="user", endless=true, nullDefault=true) Member member) {
		Member effectiveMember = member == null ? event.getMember() : member;
		User user = member == null ? event.getAuthor() : effectiveMember.getUser();

		Bson filter = Filters.and(
			Filters.eq("userId", effectiveMember.getIdLong()),
			Filters.eq("item.type", ItemType.ROD.getId())
		);

		Document data = event.getMongo().getItem(filter, Projections.include("item"));
		if (data == null) {
			event.replyFailure("That user does not have a fishing rod").queue();
			return;
		}

		Rod rod = Rod.fromData(event.getBot().getEconomyManager(), data.get("item", Document.class));

		EmbedBuilder embed = new EmbedBuilder()
			.setAuthor(user.getName() + "'s " + rod.getName(), null, user.getEffectiveAvatarUrl())
			.setColor(effectiveMember.getColorRaw())
			.setThumbnail("https://emojipedia-us.s3.amazonaws.com/thumbs/120/twitter/147/fishing-pole-and-fish_1f3a3.png")
			.addField("Durability", rod.getCurrentDurability() + "/" + rod.getMaxDurability(), false)
			.addField("Current Price", String.format("$%,d", rod.getCurrentPrice()), false)
			.addField("Price", String.format("$%,d", rod.getPrice()), false)
			.addField("Yield", String.format("$%,d to $%,d", rod.getMinYield(), rod.getMaxYield()), false);

		event.reply(embed.build()).queue();
	}

	@Command(value="repair", description="Repair your current fishing rod with the material it is made from")
	@CommandId(383)
	@Examples({"fishing rod repair 10", "fishing rod repair all"})
	public void repair(Sx4CommandEvent event, @Argument(value="durability") @Options("all") Alternative<Integer> option) {
		Bson filter = Filters.and(
			Filters.eq("userId", event.getAuthor().getIdLong()),
			Filters.eq("item.type", ItemType.ROD.getId())
		);

		Document data = event.getMongo().getItem(filter, Projections.include("item"));
		if (data == null) {
			event.replyFailure("You do not have a fishing rod").queue();
			return;
		}

		Rod rod = Rod.fromData(event.getBot().getEconomyManager(), data.get("item", Document.class));

		CraftItem item = rod.getRepairItem();
		if (item == null) {
			event.replyFailure("That fishing rod is not repairable").queue();
			return;
		}

		int maxDurability = rod.getMaxDurability() - rod.getCurrentDurability();
		if (maxDurability <= 0) {
			event.replyFailure("Your fishing rod is already at full durability").queue();
			return;
		}

		int durability;
		if (option.isAlternative()) {
			durability = maxDurability;
		} else {
			int amount = option.getValue();
			if (amount > maxDurability) {
				event.reply("You can only repair your fishing rod by **" + maxDurability + "** durability :no_entry:").queue();
				return;
			}

			durability = amount;
		}

		int itemCount = (int) Math.ceil((((double) rod.getPrice() / item.getPrice()) / rod.getMaxDurability()) * durability);

		List<Button> buttons = List.of(Button.success("yes", "Yes"), Button.danger("no", "No"));

		event.reply("It will cost you `" + itemCount + " " + item.getName() + "` to repair your fishing rod by **" + durability + "** durability, are you sure you want to repair it?").setActionRow(buttons).submit().thenCompose(message -> {
			return new Waiter<>(event.getBot(), ButtonClickEvent.class)
				.setPredicate(e -> {
					Button button = e.getButton();
					return button != null && button.getId().equals("yes") && e.getMessageIdLong() == message.getIdLong() && e.getUser().getIdLong() == event.getAuthor().getIdLong();
				})
				.setCancelPredicate(e -> {
					Button button = e.getButton();
					return button != null && button.getId().equals("no") && e.getMessageIdLong() == message.getIdLong() && e.getUser().getIdLong() == event.getAuthor().getIdLong();
				})
				.setTimeout(60)
				.start();
		}).thenCompose(e -> {
			List<Bson> update = List.of(Operators.set("amount", Operators.let(new Document("amount", Operators.ifNull("$amount", 0)), Operators.cond(Operators.lte(itemCount, "$$amount"), Operators.subtract("$$amount", itemCount), "$$amount"))));
			return event.getMongo().updateItem(Filters.and(Filters.eq("item.id", item.getId()), Filters.eq("userId", event.getAuthor().getIdLong())), update, new UpdateOptions());
		}).thenCompose(result -> {
			if (result.getMatchedCount() == 0 || result.getModifiedCount() == 0) {
				event.replyFailure("You do not have `" + itemCount + " " + item.getName() + "`").queue();
				return CompletableFuture.completedFuture(null);
			}

			List<Bson> update = List.of(Operators.set("item.currentDurability", Operators.cond(Operators.eq("$item.currentDurability", rod.getCurrentDurability()), Operators.add("$item.currentDurability", durability), "$item.currentDurability")));

			return event.getMongo().updateItem(Filters.and(Filters.eq("item.id", rod.getId()), Filters.eq("userId", event.getAuthor().getIdLong())), update, new UpdateOptions());
		}).whenComplete((result, exception) -> {
			Throwable cause = exception instanceof CompletionException ? exception.getCause() : exception;
			if (cause instanceof CancelException) {
				event.replySuccess("Cancelled").queue();
				return;
			} else if (cause instanceof TimeoutException) {
				event.reply("Timed out :stopwatch:").queue();
				return;
			}

			if (ExceptionUtility.sendExceptionally(event, exception) || result == null) {
				return;
			}

			if (result.getMatchedCount() == 0) {
				event.replyFailure("You no longer have that fishing rod").queue();
				return;
			}

			if (result.getMatchedCount() == 0) {
				event.replyFailure("The durability of your fishing rod has changed").queue();
				return;
			}

			event.replySuccess("You just repaired your fishing rod by **" + durability + "** durability").queue();
		});
	}

}

