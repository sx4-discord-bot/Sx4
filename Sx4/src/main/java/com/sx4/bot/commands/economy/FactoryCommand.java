package com.sx4.bot.commands.economy;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.command.Command;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.sx4.bot.annotations.argument.AlternativeOptions;
import com.sx4.bot.annotations.command.BotPermissions;
import com.sx4.bot.annotations.command.CommandId;
import com.sx4.bot.annotations.command.Examples;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.database.mongo.model.Operators;
import com.sx4.bot.entities.argument.Alternative;
import com.sx4.bot.entities.economy.item.*;
import com.sx4.bot.paged.PagedResult;
import com.sx4.bot.utility.EconomyUtility;
import com.sx4.bot.utility.ExceptionUtility;
import com.sx4.bot.utility.TimeUtility;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.*;
import java.util.stream.Collectors;

public class FactoryCommand extends Sx4Command {

	public static final long COOLDOWN = 43200L;


	public FactoryCommand() {
		super("factory", 394);

		super.setDescription("Factories give money every 12 hours, the more factories the more money");
		super.setExamples("factory shop", "factory buy", "factory collect");
		super.setCategoryAll(ModuleCategory.ECONOMY);
	}

	public void onCommand(Sx4CommandEvent event) {
		event.replyHelp().queue();
	}

	@Command(value="shop", description="View all the factories you can buy")
	@CommandId(395)
	@Examples({"factory shop"})
	@BotPermissions(permissions={Permission.MESSAGE_EMBED_LINKS})
	public void shop(Sx4CommandEvent event) {
		List<Factory> factories = event.getBot().getEconomyManager().getItems(Factory.class);

		PagedResult<Factory> paged = new PagedResult<>(event.getBot(), factories)
			.setPerPage(15)
			.setCustomFunction(page -> {
				EmbedBuilder embed = new EmbedBuilder()
					.setAuthor("Factory Shop", null, event.getSelfUser().getEffectiveAvatarUrl())
					.setTitle("Page " + page.getPage() + "/" + page.getMaxPage())
					.setDescription("Factories are a good way to make money from materials you have gained through mining")
					.setFooter(PagedResult.DEFAULT_FOOTER_TEXT);

				page.forEach((factory, index) -> {
					ItemStack<Material> cost = factory.getCost();
					embed.addField(factory.getName(), "Price: " + cost.getAmount() + " " + cost.getItem().getName(), true);
				});

				return new MessageCreateBuilder().setEmbeds(embed.build());
			});

		paged.execute(event);
	}

	@Command(value="buy", description="Buy a factory with some materials")
	@CommandId(396)
	@Examples({"factory buy 5 Shoe Factory", "factory buy Shoe Factory", "factory buy all"})
	@BotPermissions(permissions={Permission.MESSAGE_EMBED_LINKS})
	public void buy(Sx4CommandEvent event, @Argument(value="factories", endless=true) @AlternativeOptions("all") Alternative<ItemStack<Factory>> option) {
		ItemStack<Factory> stack = option.getValue();
		event.getMongo().withTransaction(session -> {
			Bson userFilter = Filters.eq("userId", event.getAuthor().getIdLong()), filter;
			List<Factory> factories;
			if (stack == null) {
				filter = Filters.and(userFilter, Filters.eq("item.type", ItemType.MATERIAL.getId()));
				factories = event.getBot().getEconomyManager().getItems(Factory.class);
			} else {
				Factory factory = stack.getItem();

				filter = Filters.and(userFilter, Filters.eq("item.id", factory.getCost().getItem().getId()));
				factories = List.of(factory);
			}

			List<Document> materials = event.getMongo().getItems().find(session, filter).projection(Projections.include("amount", "item.id")).into(new ArrayList<>());

			List<ItemStack<Factory>> boughtFactories = new ArrayList<>();
			Factories : for (Factory factory : factories) {
				ItemStack<Material> cost = factory.getCost();
				Material costMaterial = cost.getItem();
				for (Document material : materials) {
					int id = material.getEmbedded(List.of("item", "id"), Integer.class);
					if (costMaterial.getId() == id) {
						long buyableAmount = (long) Math.floor((double) material.getLong("amount") / cost.getAmount());
						long amount = stack == null ? buyableAmount : stack.getAmount();
						if (amount == 0 || amount > buyableAmount) {
							continue Factories;
						}

						event.getMongo().getItems().updateOne(session, Filters.and(userFilter, Filters.eq("item.id", id)), Updates.inc("amount", -amount * cost.getAmount()));

						List<Bson> update = List.of(
							Operators.set("item", factory.toData()),
							Operators.set("amount", Operators.add(Operators.ifNull("$amount", 0L), amount))
						);

						event.getMongo().getItems().updateOne(session, Filters.and(userFilter, Filters.eq("item.id", factory.getId())), update, new UpdateOptions().upsert(true));

						boughtFactories.add(new ItemStack<>(factory, amount));
					}
				}
			}

			return boughtFactories;
		}).whenComplete((factories, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			if (factories.isEmpty()) {
				event.replyFailure("You cannot afford " + (stack == null ? "any factories" : "`" + stack.getAmount() + " " + stack.getItem().getName() + "`")).queue();
				return;
			}

			String factoriesBought = factories.stream().sorted(Collections.reverseOrder(Comparator.comparingLong(ItemStack::getAmount))).map(ItemStack::toString).collect(Collectors.joining("\n• "));

			EmbedBuilder embed = new EmbedBuilder()
				.setColor(event.getMember().getColor())
				.setAuthor(event.getAuthor().getName(), null, event.getAuthor().getEffectiveAvatarUrl())
				.setDescription("With all your materials you have bought the following factories\n\n• " + factoriesBought);

			event.reply(embed.build()).queue();
		});
	}

	@Command(value="collect", description="Collect money from your owned factories")
	@CommandId(397)
	@Examples({"factory collect"})
	@BotPermissions(permissions={Permission.MESSAGE_EMBED_LINKS})
	public void collect(Sx4CommandEvent event) {
		event.getMongo().withTransaction(session -> {
			Bson filter = Filters.and(
				Filters.eq("userId", event.getAuthor().getIdLong()),
				Filters.eq("item.type", ItemType.FACTORY.getId())
			);

			List<Document> factories = event.getMongo().getItems().find(session, filter).projection(Projections.include("amount", "item.id", "resets")).into(new ArrayList<>());
			if (factories.isEmpty()) {
				event.replyFailure("You do not have any factories").queue();
				session.abortTransaction();
				return null;
			}

			StringJoiner content = new StringJoiner("\n");
			long money = 0, lowestReset = Long.MAX_VALUE;
			for (Document data : factories) {
				CooldownItemStack<Factory> stack = new CooldownItemStack<>(event.getBot().getEconomyManager(), data);
				Factory factory = stack.getItem();

				if (stack.getAmount() == 0) {
					continue;
				}

				long nextReset = stack.getTimeRemaining();
				lowestReset = Math.min(nextReset, lowestReset);

				long amount = stack.getUsableAmount();
				if (amount == 0) {
					continue;
				}

				long gained = factory.getYield() * amount;

				money += gained;
				content.add(String.format("• %,d %s: $%,d", amount, factory.getName(), gained));

				event.getMongo().getItems().updateOne(Filters.and(Filters.eq("userId", event.getAuthor().getIdLong()), Filters.eq("item.id", factory.getId())), List.of(EconomyUtility.getResetsUpdate(amount, FactoryCommand.COOLDOWN)));
			}

			if (lowestReset == Long.MAX_VALUE) {
				event.replyFailure("You do not have any factories").queue();
				session.abortTransaction();
				return null;
			}

			if (money == 0) {
				event.reply("Slow down! You can collect from your factory in " + TimeUtility.LONG_TIME_FORMATTER.parse(lowestReset) + " :stopwatch:").queue();
				session.abortTransaction();
				return null;
			}

			event.getMongo().getUsers().updateOne(session, Filters.eq("_id", event.getAuthor().getIdLong()), Updates.inc("economy.balance", money), new UpdateOptions().upsert(true));

			EmbedBuilder embed = new EmbedBuilder()
				.setAuthor(event.getAuthor().getName(), null, event.getAuthor().getEffectiveAvatarUrl())
				.setColor(event.getMember().getColorRaw())
				.setDescription(String.format("Your factories made you **$%,d**\n\n%s", money, content));

			return embed.build();
		}).whenComplete((embed, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception) || embed == null) {
				return;
			}

			event.reply(embed).queue();
		});
	}

}
