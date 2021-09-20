package com.sx4.bot.commands.economy;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.command.Command;
import com.mongodb.client.model.*;
import com.mongodb.client.result.UpdateResult;
import com.sx4.bot.annotations.argument.AlternativeOptions;
import com.sx4.bot.annotations.argument.DefaultNumber;
import com.sx4.bot.annotations.argument.Limit;
import com.sx4.bot.annotations.command.BotPermissions;
import com.sx4.bot.annotations.command.CommandId;
import com.sx4.bot.annotations.command.Examples;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.database.mongo.model.Operators;
import com.sx4.bot.entities.argument.Alternative;
import com.sx4.bot.entities.economy.item.Axe;
import com.sx4.bot.entities.economy.item.CraftItem;
import com.sx4.bot.entities.economy.item.ItemStack;
import com.sx4.bot.entities.economy.item.ItemType;
import com.sx4.bot.entities.economy.upgrade.Upgrade;
import com.sx4.bot.paged.PagedResult;
import com.sx4.bot.utility.ButtonUtility;
import com.sx4.bot.utility.EconomyUtility;
import com.sx4.bot.utility.ExceptionUtility;
import com.sx4.bot.utility.NumberUtility;
import com.sx4.bot.waiter.Waiter;
import com.sx4.bot.waiter.exception.CancelException;
import com.sx4.bot.waiter.exception.TimeoutException;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.interactions.components.Button;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;

public class AxeCommand extends Sx4Command {

	public AxeCommand() {
		super("axe", 387);

		super.setDescription("View everything about axes");
		super.setExamples("axe shop", "axe buy", "axe info");
		super.setCategoryAll(ModuleCategory.ECONOMY);
	}

	public void onCommand(Sx4CommandEvent event) {
		event.replyHelp().queue();
	}

	@Command(value="shop", description="View all the axes you are able to buy or craft")
	@CommandId(388)
	@Examples({"axe shop"})
	@BotPermissions(permissions={Permission.MESSAGE_EMBED_LINKS})
	public void shop(Sx4CommandEvent event) {
		List<Axe> axes = event.getBot().getEconomyManager().getItems(Axe.class);

		PagedResult<Axe> paged = new PagedResult<>(event.getBot(), axes)
			.setPerPage(12)
			.setCustomFunction(page -> {
				EmbedBuilder embed = new EmbedBuilder()
					.setAuthor("Axe Shop", null, event.getSelfUser().getEffectiveAvatarUrl())
					.setTitle("Page " + page.getPage() + "/" + page.getMaxPage())
					.setDescription("Axes are a good way to get some wood for crafting")
					.setFooter(PagedResult.DEFAULT_FOOTER_TEXT);

				page.forEach((axe, index) -> {
					List<ItemStack<CraftItem>> items = axe.getCraft();
					String craft = items.isEmpty() ? "None" : items.stream().map(ItemStack::toString).collect(Collectors.joining("\n"));
					embed.addField(axe.getName(), String.format("Price: $%,d\nCraft: %s\nDurability: %,d", axe.getPrice(), craft, axe.getMaxDurability()), true);
				});

				return new MessageBuilder().setEmbeds(embed.build());
			});

		paged.execute(event);
	}

	@Command(value="buy", description="Buy a axe from the `axe shop`")
	@CommandId(389)
	@Examples({"axe buy Wooden Axe", "axe buy Wooden"})
	public void buy(Sx4CommandEvent event, @Argument(value="axe", endless=true) Axe axe) {
		event.getMongo().withTransaction(session -> {
			Bson filter = Filters.and(
				Filters.eq("userId", event.getAuthor().getIdLong()),
				Filters.eq("item.type", ItemType.AXE.getId())
			);

			Document axeData = event.getMongo().getItems().find(session, filter).projection(Projections.include("_id")).first();
			if (axeData != null) {
				event.replyFailure("You already own a axe").queue();
				session.abortTransaction();
				return;
			}

			FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().returnDocument(ReturnDocument.BEFORE).projection(Projections.include("economy.balance")).upsert(true);

			Document data = event.getMongo().getUsers().findOneAndUpdate(session, Filters.eq("_id", event.getAuthor().getIdLong()), List.of(EconomyUtility.decreaseBalanceUpdate(axe.getPrice())), options);
			if (data == null || data.getEmbedded(List.of("economy", "balance"), 0L) < axe.getPrice()) {
				event.replyFailure("You cannot afford a **" + axe.getName() + "**").queue();
				session.abortTransaction();
				return;
			}

			Document insertData = new Document("userId", event.getAuthor().getIdLong())
				.append("amount", 1L)
				.append("item", axe.toData());

			event.getMongo().insertItem(insertData);
		}).whenComplete((updated, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			if (updated) {
				event.replySuccess("You just bought a **" + axe.getName() + "**").queue();
			}
		});
	}

	@Command(value="craft", description="Craft a axe from the `axe shop`")
	@CommandId(390)
	@Examples({"axe craft Wooden Axe", "axe craft Wooden"})
	public void craft(Sx4CommandEvent event, @Argument(value="axe", endless=true) Axe axe) {
		event.getMongo().withTransaction(session -> {
			Bson filter = Filters.and(
				Filters.eq("userId", event.getAuthor().getIdLong()),
				Filters.eq("item.type", ItemType.AXE.getId())
			);

			Document rodData = event.getMongo().getItems().find(session, filter).projection(Projections.include("_id")).first();
			if (rodData != null) {
				event.replyFailure("You already own a axe").queue();
				session.abortTransaction();
				return;
			}

			List<ItemStack<CraftItem>> craft = axe.getCraft();
			if (craft.isEmpty()) {
				event.replyFailure("You cannot craft this axe").queue();
				session.abortTransaction();
				return;
			}

			for (ItemStack<CraftItem> stack : craft) {
				CraftItem item = stack.getItem();

				Bson itemFilter = Filters.and(
					Filters.eq("userId", event.getAuthor().getIdLong()),
					Filters.eq("item.id", item.getId())
				);

				List<Bson> update = List.of(Operators.set("amount", Operators.let(new Document("amount", Operators.ifNull("$amount", 0L)), Operators.cond(Operators.lte(stack.getAmount(), "$$amount"), Operators.subtract("$$amount", stack.getAmount()), "$$amount"))));

				UpdateResult result = event.getMongo().getItems().updateOne(session, itemFilter, update);
				if (result.getModifiedCount() == 0) {
					event.replyFailure("You do not have `" + stack.getAmount() + " " + item.getName() + "`").queue();
					session.abortTransaction();
					return;
				}
			}

			Document insertData = new Document("userId", event.getAuthor().getIdLong())
				.append("amount", 1L)
				.append("item", axe.toData());

			event.getMongo().getItems().insertOne(session, insertData);
		}).whenComplete((updated, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			if (updated) {
				event.replySuccess("You just crafted a **" + axe.getName() + "**").queue();
			}
		});
	}

	@Command(value="info", aliases={"information"}, description="View information on a users axe")
	@CommandId(391)
	@Examples({"axe info", "axe info @Shea#6653", "axe info Shea"})
	@BotPermissions(permissions={Permission.MESSAGE_EMBED_LINKS})
	public void info(Sx4CommandEvent event, @Argument(value="user", endless=true, nullDefault=true) Member member) {
		Member effectiveMember = member == null ? event.getMember() : member;
		User user = member == null ? event.getAuthor() : effectiveMember.getUser();

		Bson filter = Filters.and(
			Filters.eq("userId", effectiveMember.getIdLong()),
			Filters.eq("item.type", ItemType.AXE.getId())
		);

		Document data = event.getMongo().getItem(filter, Projections.include("item"));
		if (data == null) {
			event.replyFailure("That user does not have an axe").queue();
			return;
		}

		Axe axe = Axe.fromData(event.getBot().getEconomyManager(), data.get("item", Document.class));

		EmbedBuilder embed = new EmbedBuilder()
			.setAuthor(user.getName() + "'s " + axe.getName(), null, user.getEffectiveAvatarUrl())
			.setColor(effectiveMember.getColorRaw())
			.setThumbnail("https://www.shareicon.net/data/2016/09/02/823994_ax_512x512.png")
			.addField("Durability", axe.getDurability() + "/" + axe.getMaxDurability(), false)
			.addField("Current Price", String.format("$%,d", axe.getCurrentPrice()), false)
			.addField("Price", String.format("$%,d", axe.getPrice()), false)
			.addField("Max Materials", String.valueOf(axe.getMaxMaterials()), false)
			.addField("Multiplier", NumberUtility.DEFAULT_DECIMAL_FORMAT.format(axe.getMultiplier()), false);

		event.reply(embed.build()).queue();
	}

	@Command(value="repair", description="Repair your current axe with the material it is made from")
	@CommandId(392)
	@Examples({"axe repair 10", "axe repair all"})
	public void repair(Sx4CommandEvent event, @Argument(value="durability") @AlternativeOptions("all") Alternative<Integer> option) {
		Bson filter = Filters.and(
			Filters.eq("userId", event.getAuthor().getIdLong()),
			Filters.eq("item.type", ItemType.AXE.getId())
		);

		Document data = event.getMongo().getItem(filter, Projections.include("item"));
		if (data == null) {
			event.replyFailure("You do not have a axe").queue();
			return;
		}

		Axe axe = Axe.fromData(event.getBot().getEconomyManager(), data.get("item", Document.class));

		CraftItem item = axe.getRepairItem();
		if (item == null) {
			event.replyFailure("That axe is not repairable").queue();
			return;
		}

		int maxDurability = axe.getMaxDurability() - axe.getDurability();
		if (maxDurability <= 0) {
			event.replyFailure("Your axe is already at full durability").queue();
			return;
		}

		int durability;
		if (option.isAlternative()) {
			durability = maxDurability;
		} else {
			int amount = option.getValue();
			if (amount > maxDurability) {
				event.reply("You can only repair your axe by **" + maxDurability + "** durability :no_entry:").queue();
				return;
			}

			durability = amount;
		}

		int itemCount = (int) Math.ceil((((double) axe.getPrice() / item.getPrice()) / axe.getMaxDurability()) * durability);

		List<Button> buttons = List.of(Button.success("yes", "Yes"), Button.danger("no", "No"));

		event.reply("It will cost you `" + itemCount + " " + item.getName() + "` to repair your axe by **" + durability + "** durability, are you sure you want to repair it?").setActionRow(buttons).submit().thenCompose(message -> {
			return new Waiter<>(event.getBot(), ButtonClickEvent.class)
				.setPredicate(e -> ButtonUtility.handleButtonConfirmation(e, message, event.getAuthor()))
				.setCancelPredicate(e -> ButtonUtility.handleButtonCancellation(e, message, event.getAuthor()))
				.onFailure(e -> ButtonUtility.handleButtonFailure(e, message))
				.setTimeout(60)
				.start();
		}).whenComplete((e, exception) -> {
			Throwable cause = exception instanceof CompletionException ? exception.getCause() : exception;
			if (cause instanceof CancelException) {
				GenericEvent cancelEvent = ((CancelException) cause).getEvent();
				if (cancelEvent != null) {
					((ButtonClickEvent) cancelEvent).reply("Cancelled " + event.getConfig().getSuccessEmote()).queue();
				}

				return;
			} else if (cause instanceof TimeoutException) {
				event.reply("Timed out :stopwatch:").queue();
				return;
			} else if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			List<Bson> update = List.of(Operators.set("amount", Operators.let(new Document("amount", Operators.ifNull("$amount", 0L)), Operators.cond(Operators.lte(itemCount, "$$amount"), Operators.subtract("$$amount", itemCount), "$$amount"))));

			event.getMongo().updateItem(Filters.and(Filters.eq("item.id", item.getId()), Filters.eq("userId", event.getAuthor().getIdLong())), update, new UpdateOptions()).thenCompose(result -> {
				if (result.getMatchedCount() == 0 || result.getModifiedCount() == 0) {
					e.reply("You do not have `" + itemCount + " " + item.getName() + "` " + event.getConfig().getFailureEmote()).queue();
					return CompletableFuture.completedFuture(null);
				}

				List<Bson> itemUpdate = List.of(Operators.set("item.durability", Operators.cond(Operators.eq("$item.durability", axe.getDurability()), Operators.add("$item.durability", durability), "$item.durability")));

				return event.getMongo().updateItem(Filters.and(Filters.eq("item.id", axe.getId()), Filters.eq("userId", event.getAuthor().getIdLong())), itemUpdate, new UpdateOptions());
			}).whenComplete((result, databaseException) -> {
				if (ExceptionUtility.sendExceptionally(event, databaseException) || result == null) {
					return;
				}

				if (result.getMatchedCount() == 0) {
					e.reply("You no longer have that axe " + event.getConfig().getFailureEmote()).queue();
					return;
				}

				if (result.getMatchedCount() == 0) {
					e.reply("The durability of your axe has changed " + event.getConfig().getFailureEmote()).queue();
					return;
				}

				e.reply("You just repaired your axe by **" + durability + "** durability " + event.getConfig().getSuccessEmote()).queue();
			});
		});
	}

	@Command(value="upgrade", description="Upgrade your axe by a certain attribute")
	@CommandId(429)
	@Examples({"axe upgrade multiplier", "axe upgrade durability 5"})
	public void upgrade(Sx4CommandEvent event, @Argument(value="upgrade") Upgrade upgrade, @Argument(value="upgrades") @DefaultNumber(1) @Limit(min=1, max=100) int upgrades) {
		if (!upgrade.containsType(ItemType.AXE)) {
			event.replyFailure("You can not use that upgrade on a axe").queue();
			return;
		}

		event.getMongo().withTransaction(session -> {
			Document data = event.getMongo().getItems().find(session, Filters.and(Filters.eq("userId", event.getAuthor().getIdLong()), Filters.eq("item.type", ItemType.AXE.getId()))).first();
			if (data == null) {
				event.replyFailure("You do not have a axe").queue();
				session.abortTransaction();
				return null;
			}

			Document item = data.get("item", Document.class);
			Axe defaultAxe = event.getBot().getEconomyManager().getItemById(item.getInteger("id"), Axe.class);
			Axe axe = new Axe(item, defaultAxe);

			int currentUpgrades = axe.getUpgrades();
			long price = 0;
			for (int i = 0; i < upgrades; i++) {
				price += Math.round(0.015D * defaultAxe.getPrice() * currentUpgrades++ + 0.025D * defaultAxe.getPrice());
			}

			UpdateResult result = event.getMongo().getUsers().updateOne(session, Filters.eq("_id", event.getAuthor().getIdLong()), List.of(EconomyUtility.decreaseBalanceUpdate(price)));
			if (result.getModifiedCount() == 0) {
				event.replyFormat("You do not have **$%,d** %s", price, event.getConfig().getFailureEmote()).queue();
				session.abortTransaction();
				return null;
			}

			List<Bson> update = new ArrayList<>();
			update.add(Operators.set("item.upgrades", Operators.add(Operators.ifNull("$item.upgrades", 0), upgrades)));
			update.add(Operators.set("item.price", Operators.add("$item.price", Math.round(defaultAxe.getPrice() * 0.015D) * upgrades)));

			if (upgrade == Upgrade.DURABILITY) {
				int increase = (int) upgrade.getValue() * upgrades;

				update.add(Operators.set("item.durability", Operators.add("$item.durability", increase)));
				update.add(Operators.set("item.maxDurability", Operators.add("$item.maxDurability", increase)));
			} else if (upgrade == Upgrade.MULTIPLIER) {
				double increase = defaultAxe.getMultiplier() * upgrade.getValue() * upgrades;

				update.add(Operators.set("item.multiplier", Operators.add("$item.multiplier", increase)));
			}

			event.getMongo().getItems().updateOne(session, Filters.and(Filters.eq("userId", event.getAuthor().getIdLong()), Filters.eq("item.id", axe.getId())), update);

			return String.format("You just upgraded %s %d time%s for your `%s` for **$%,d**", upgrade.getName().toLowerCase(), upgrades, (upgrades == 1 ? "" : "s"), axe.getName(), price);
		}).whenComplete((message, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception) || message == null) {
				return;
			}

			event.replySuccess(message).queue();
		});
	}

	@Command(value="upgrades", description="View all the upgrades you can use on a axe")
	@CommandId(430)
	@Examples({"pickaxe upgrades"})
	public void upgrades(Sx4CommandEvent event) {
		EnumSet<Upgrade> upgrades = Upgrade.getUpgrades(ItemType.AXE);

		PagedResult<Upgrade> paged = new PagedResult<>(event.getBot(), Arrays.asList(upgrades.toArray(Upgrade[]::new)))
			.setPerPage(3)
			.setCustomFunction(page -> {
				EmbedBuilder embed = new EmbedBuilder()
					.setTitle("Axe Upgrades");

				page.forEach((upgrade, index) -> embed.addField(upgrade.getName(), upgrade.getDescription(), false));

				return new MessageBuilder().setEmbeds(embed.build());
			});

		paged.execute(event);
	}

}

