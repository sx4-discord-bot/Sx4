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
import com.sx4.bot.entities.economy.item.CraftItem;
import com.sx4.bot.entities.economy.item.ItemStack;
import com.sx4.bot.entities.economy.item.ItemType;
import com.sx4.bot.entities.economy.item.Pickaxe;
import com.sx4.bot.entities.economy.upgrade.Upgrade;
import com.sx4.bot.paged.PagedResult;
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

public class PickaxeCommand extends Sx4Command {

	public PickaxeCommand() {
		super("pickaxe", 359);

		super.setDescription("View everything about pickaxes");
		super.setAliases("pick");
		super.setExamples("pickaxe shop", "pickaxe buy", "pickaxe info");
		super.setCategoryAll(ModuleCategory.ECONOMY);
	}

	public void onCommand(Sx4CommandEvent event) {
		event.replyHelp().queue();
	}

	@Command(value="shop", description="View all the pickaxes you are able to buy or craft")
	@CommandId(360)
	@Examples({"pickaxe shop"})
	@BotPermissions(permissions={Permission.MESSAGE_EMBED_LINKS})
	public void shop(Sx4CommandEvent event) {
		List<Pickaxe> pickaxes = event.getBot().getEconomyManager().getItems(Pickaxe.class);

		PagedResult<Pickaxe> paged = new PagedResult<>(event.getBot(), pickaxes)
			.setPerPage(12)
			.setCustomFunction(page -> {
				EmbedBuilder embed = new EmbedBuilder()
					.setAuthor("Pickaxe Shop", null, event.getSelfUser().getEffectiveAvatarUrl())
					.setTitle("Page " + page.getPage() + "/" + page.getMaxPage())
					.setDescription("Pickaxes are a good way to gain some extra money aswell as some materials")
					.setFooter(PagedResult.DEFAULT_FOOTER_TEXT);

				page.forEach((pickaxe, index) -> {
					List<ItemStack<CraftItem>> items = pickaxe.getCraft();
					String craft = items.isEmpty() ? "None" : items.stream().map(ItemStack::toString).collect(Collectors.joining("\n"));
					embed.addField(pickaxe.getName(), String.format("Price: $%,d\nCraft: %s\nDurability: %,d", pickaxe.getPrice(), craft, pickaxe.getMaxDurability()), true);
				});

				return new MessageBuilder().setEmbeds(embed.build());
			});

		paged.execute(event);
	}

	@Command(value="buy", description="Buy a pickaxe from the `pickaxe shop`")
	@CommandId(361)
	@Examples({"pickaxe buy Wooden Pickaxe", "pickaxe buy Wooden"})
	public void buy(Sx4CommandEvent event, @Argument(value="pickaxe", endless=true) Pickaxe pickaxe) {
		event.getMongo().withTransaction(session -> {
			Bson filter = Filters.and(
				Filters.eq("userId", event.getAuthor().getIdLong()),
				Filters.eq("item.type", ItemType.PICKAXE.getId())
			);

			Document pickaxeData = event.getMongo().getItems().find(session, filter).projection(Projections.include("_id")).first();
			if (pickaxeData != null) {
				event.replyFailure("You already own a pickaxe").queue();
				session.abortTransaction();
				return;
			}

			FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().returnDocument(ReturnDocument.BEFORE).projection(Projections.include("economy.balance")).upsert(true);

			Document data = event.getMongo().getUsers().findOneAndUpdate(session, Filters.eq("_id", event.getAuthor().getIdLong()), List.of(EconomyUtility.decreaseBalanceUpdate(pickaxe.getPrice())), options);
			if (data == null || data.getEmbedded(List.of("economy", "balance"), 0L) < pickaxe.getPrice()) {
				event.replyFailure("You cannot afford a **" + pickaxe.getName() + "**").queue();
				session.abortTransaction();
				return;
			}

			Document insertData = new Document("userId", event.getAuthor().getIdLong())
				.append("amount", 1L)
				.append("item", pickaxe.toData());

			event.getMongo().insertItem(insertData);
		}).whenComplete((updated, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			if (updated) {
				event.replySuccess("You just bought a **" + pickaxe.getName() + "**").queue();
			}
		});
	}

	@Command(value="craft", description="Craft a pickaxe from the `pickaxe shop`")
	@CommandId(364)
	@Examples({"pickaxe craft Wooden Pickaxe", "pickaxe craft Wooden"})
	public void craft(Sx4CommandEvent event, @Argument(value="pickaxe", endless=true) Pickaxe pickaxe) {
		event.getMongo().withTransaction(session -> {
			Bson filter = Filters.and(
				Filters.eq("userId", event.getAuthor().getIdLong()),
				Filters.eq("item.type", ItemType.PICKAXE.getId())
			);

			Document pickaxeData = event.getMongo().getItems().find(session, filter).projection(Projections.include("_id")).first();
			if (pickaxeData != null) {
				event.replyFailure("You already own a pickaxe").queue();
				session.abortTransaction();
				return;
			}

			List<ItemStack<CraftItem>> craft = pickaxe.getCraft();
			if (craft.isEmpty()) {
				event.replyFailure("You cannot craft this pickaxe").queue();
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
				.append("item", pickaxe.toData());

			event.getMongo().getItems().insertOne(session, insertData);
		}).whenComplete((updated, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			if (updated) {
				event.replySuccess("You just crafted a **" + pickaxe.getName() + "**").queue();
			}
		});
	}

	@Command(value="info", aliases={"information"}, description="View information on a users pickaxe")
	@CommandId(362)
	@Examples({"pickaxe info", "pickaxe info @Shea#6653", "pickaxe info Shea"})
	@BotPermissions(permissions={Permission.MESSAGE_EMBED_LINKS})
	public void info(Sx4CommandEvent event, @Argument(value="user", endless=true, nullDefault=true) Member member) {
		Member effectiveMember = member == null ? event.getMember() : member;
		User user = member == null ? event.getAuthor() : effectiveMember.getUser();

		Bson filter = Filters.and(
			Filters.eq("userId", effectiveMember.getIdLong()),
			Filters.eq("item.type", ItemType.PICKAXE.getId())
		);

		Document data = event.getMongo().getItem(filter, Projections.include("item"));
		if (data == null) {
			event.replyFailure("That user does not have a pickaxe").queue();
			return;
		}

		Pickaxe pickaxe = Pickaxe.fromData(event.getBot().getEconomyManager(), data.get("item", Document.class));

		EmbedBuilder embed = new EmbedBuilder()
			.setAuthor(user.getName() + "'s " + pickaxe.getName(), null, user.getEffectiveAvatarUrl())
			.setColor(effectiveMember.getColorRaw())
			.setThumbnail("https://emojipedia-us.s3.amazonaws.com/thumbs/120/twitter/131/pick_26cf.png")
			.addField("Durability", pickaxe.getDurability() + "/" + pickaxe.getMaxDurability(), false)
			.addField("Current Price", String.format("$%,d", pickaxe.getCurrentPrice()), false)
			.addField("Price", String.format("$%,d", pickaxe.getPrice()), false)
			.addField("Yield", String.format("$%,d to $%,d", pickaxe.getMinYield(), pickaxe.getMaxYield()), false)
			.addField("Multiplier", NumberUtility.DEFAULT_DECIMAL_FORMAT.format(pickaxe.getMultiplier()), false);

		event.reply(embed.build()).queue();
	}

	@Command(value="repair", description="Repair your current pickaxe with the material it is made from")
	@CommandId(363)
	@Examples({"pickaxe repair 10", "pickaxe repair all"})
	public void repair(Sx4CommandEvent event, @Argument(value="durability") @AlternativeOptions("all") Alternative<Integer> option) {
		Bson filter = Filters.and(
			Filters.eq("userId", event.getAuthor().getIdLong()),
			Filters.eq("item.type", ItemType.PICKAXE.getId())
		);

		Document data = event.getMongo().getItem(filter, Projections.include("item"));
		if (data == null) {
			event.replyFailure("You do not have a pickaxe").queue();
			return;
		}

		Pickaxe pickaxe = Pickaxe.fromData(event.getBot().getEconomyManager(), data.get("item", Document.class));

		CraftItem item = pickaxe.getRepairItem();
		if (item == null) {
			event.replyFailure("That pickaxe is not repairable").queue();
			return;
		}

		int maxDurability = pickaxe.getMaxDurability() - pickaxe.getDurability();
		if (maxDurability <= 0) {
			event.replyFailure("Your pickaxe is already at full durability").queue();
			return;
		}

		int durability;
		if (option.isAlternative()) {
			durability = maxDurability;
		} else {
			int amount = option.getValue();
			if (amount > maxDurability) {
				event.reply("You can only repair your pickaxe by **" + maxDurability + "** durability :no_entry:").queue();
				return;
			}

			durability = amount;
		}

		int itemCount = (int) Math.ceil((((double) pickaxe.getPrice() / item.getPrice()) / pickaxe.getMaxDurability()) * durability);

		List<Button> buttons = List.of(Button.success("yes", "Yes"), Button.danger("no", "No"));

		event.reply("It will cost you `" + itemCount + " " + item.getName() + "` to repair your pickaxe by **" + durability + "** durability, are you sure you want to repair it?").setActionRow(buttons).submit().thenCompose(message -> {
			return new Waiter<>(event.getBot(), ButtonClickEvent.class)
				.setPredicate(e -> {
					Button button = e.getButton();
					return button != null && button.getId().equals("yes") && e.getMessageIdLong() == message.getIdLong() && e.getUser().getIdLong() == event.getAuthor().getIdLong();
				})
				.setCancelPredicate(e -> {
					Button button = e.getButton();
					return button != null && button.getId().equals("no") && e.getMessageIdLong() == message.getIdLong() && e.getUser().getIdLong() == event.getAuthor().getIdLong();
				})
				.setRunAfter(e -> e.deferEdit().queue())
				.setTimeout(60)
				.start();
		}).thenCompose(e -> {
			List<Bson> update = List.of(Operators.set("amount", Operators.let(new Document("amount", Operators.ifNull("$amount", 0L)), Operators.cond(Operators.lte(itemCount, "$$amount"), Operators.subtract("$$amount", itemCount), "$$amount"))));
			return event.getMongo().updateItem(Filters.and(Filters.eq("item.id", item.getId()), Filters.eq("userId", event.getAuthor().getIdLong())), update, new UpdateOptions());
		}).thenCompose(result -> {
			if (result.getMatchedCount() == 0 || result.getModifiedCount() == 0) {
				event.replyFailure("You do not have `" + itemCount + " " + item.getName() + "`").queue();
				return CompletableFuture.completedFuture(null);
			}

			List<Bson> update = List.of(Operators.set("item.durability", Operators.cond(Operators.eq("$item.durability", pickaxe.getDurability()), Operators.add("$item.durability", durability), "$item.durability")));

			return event.getMongo().updateItem(Filters.and(Filters.eq("item.id", pickaxe.getId()), Filters.eq("userId", event.getAuthor().getIdLong())), update, new UpdateOptions());
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
				event.replyFailure("You no longer have that pickaxe").queue();
				return;
			}

			if (result.getMatchedCount() == 0) {
				event.replyFailure("The durability of your pickaxe has changed").queue();
				return;
			}

			event.replySuccess("You just repaired your pickaxe by **" + durability + "** durability").queue();
		});
	}

	@Command(value="upgrade", description="Upgrade your pickaxe by a certain attribute")
	@CommandId(427)
	@Examples({"pickaxe upgrade money", "pickaxe upgrade multiplier 10", "pickaxe upgrade durability 5"})
	public void upgrade(Sx4CommandEvent event, @Argument(value="upgrade") Upgrade upgrade, @Argument(value="upgrades") @DefaultNumber(1) @Limit(min=1, max=100) int upgrades) {
		if (!upgrade.containsType(ItemType.PICKAXE)) {
			event.replyFailure("You can not use that upgrade on a pickaxe").queue();
			return;
		}

		event.getMongo().withTransaction(session -> {
			Document data = event.getMongo().getItems().find(session, Filters.and(Filters.eq("userId", event.getAuthor().getIdLong()), Filters.eq("item.type", ItemType.PICKAXE.getId()))).first();
			if (data == null) {
				event.replyFailure("You do not have a pickaxe").queue();
				session.abortTransaction();
				return null;
			}

			Document item = data.get("item", Document.class);
			Pickaxe defaultPickaxe = event.getBot().getEconomyManager().getItemById(item.getInteger("id"), Pickaxe.class);
			Pickaxe pickaxe = new Pickaxe(item, defaultPickaxe);

			int currentUpgrades = pickaxe.getUpgrades();
			long price = 0;
			for (int i = 0; i < upgrades; i++) {
				price += Math.round(0.015D * defaultPickaxe.getPrice() * currentUpgrades++ + 0.025D * defaultPickaxe.getPrice());
			}

			UpdateResult result = event.getMongo().getUsers().updateOne(session, Filters.eq("_id", event.getAuthor().getIdLong()), List.of(EconomyUtility.decreaseBalanceUpdate(price)));
			if (result.getModifiedCount() == 0) {
				event.replyFormat("You do not have **$%,d** %s", price, event.getConfig().getFailureEmote()).queue();
				session.abortTransaction();
				return null;
			}

			List<Bson> update = new ArrayList<>();
			update.add(Operators.set("item.upgrades", Operators.add(Operators.ifNull("$item.upgrades", 0), upgrades)));
			update.add(Operators.set("item.price", Operators.add("$item.price", Math.round(defaultPickaxe.getPrice() * 0.015D) * upgrades)));

			if (upgrade == Upgrade.MONEY) {
				int increase = (int) Math.round(defaultPickaxe.getMinYield() * upgrade.getValue()) * upgrades;

				update.add(Operators.set("item.minYield", Operators.add("$item.minYield", increase)));
				update.add(Operators.set("item.maxYield", Operators.add("$item.maxYield", increase)));
			} else if (upgrade == Upgrade.DURABILITY) {
				int increase = (int) upgrade.getValue() * upgrades;

				update.add(Operators.set("item.durability", Operators.add("$item.durability", increase)));
				update.add(Operators.set("item.maxDurability", Operators.add("$item.maxDurability", increase)));
			} else if (upgrade == Upgrade.MULTIPLIER) {
				double increase = defaultPickaxe.getMultiplier() * upgrade.getValue() * upgrades;

				update.add(Operators.set("item.multiplier", Operators.add("$item.multiplier", increase)));
			}

			event.getMongo().getItems().updateOne(session, Filters.and(Filters.eq("userId", event.getAuthor().getIdLong()), Filters.eq("item.id", pickaxe.getId())), update);

			return String.format("You just upgraded %s %d time%s for your `%s` for **$%,d**", upgrade.getName().toLowerCase(), upgrades, (upgrades == 1 ? "" : "s"), pickaxe.getName(), price);
		}).whenComplete((message, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception) || message == null) {
				return;
			}

			event.replySuccess(message).queue();
		});
	}

	@Command(value="upgrades", description="View all the upgrades you can use on a pickaxe")
	@CommandId(428)
	@Examples({"pickaxe upgrades"})
	public void upgrades(Sx4CommandEvent event) {
		EnumSet<Upgrade> upgrades = Upgrade.getUpgrades(ItemType.PICKAXE);

		PagedResult<Upgrade> paged = new PagedResult<>(event.getBot(), Arrays.asList(upgrades.toArray(Upgrade[]::new)))
			.setPerPage(3)
			.setCustomFunction(page -> {
				EmbedBuilder embed = new EmbedBuilder()
					.setTitle("Pickaxe Upgrades");

				page.forEach((upgrade, index) -> embed.addField(upgrade.getName(), upgrade.getDescription(), false));

				return new MessageBuilder().setEmbeds(embed.build());
			});

		paged.execute(event);
	}

}
