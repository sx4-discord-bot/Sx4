package com.sx4.bot.commands.economy;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.command.Command;
import com.mongodb.client.model.*;
import com.mongodb.client.result.UpdateResult;
import com.sx4.bot.annotations.command.BotPermissions;
import com.sx4.bot.annotations.command.CommandId;
import com.sx4.bot.annotations.command.Examples;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.database.mongo.model.Operators;
import com.sx4.bot.entities.economy.item.*;
import com.sx4.bot.managers.EconomyManager;
import com.sx4.bot.paged.PagedResult;
import com.sx4.bot.utility.EconomyUtility;
import com.sx4.bot.utility.ExceptionUtility;
import com.sx4.bot.utility.TimeUtility;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.Permission;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.*;
import java.util.stream.Collectors;

public class MinerCommand extends Sx4Command {

	public static final long COOLDOWN = 7200L;

	public MinerCommand() {
		super("miner", 401);

		super.setDescription("Miners are a great was to get a lot of materials");
		super.setExamples("miner shop", "miner buy", "miner collect");
		super.setCategoryAll(ModuleCategory.ECONOMY);
	}

	public void onCommand(Sx4CommandEvent event) {
		event.replyHelp().queue();
	}

	@Command(value="shop", description="View all the different miners you can buy")
	@CommandId(402)
	@Examples({"miner shop"})
	@BotPermissions(permissions={Permission.MESSAGE_EMBED_LINKS})
	public void shop(Sx4CommandEvent event) {
		List<Miner> miners = event.getBot().getEconomyManager().getItems(Miner.class);

		PagedResult<Miner> paged = new PagedResult<>(event.getBot(), miners)
			.setPerPage(12)
			.setCustomFunction(page -> {
				EmbedBuilder embed = new EmbedBuilder()
					.setDescription("Miners are a good way to easily gather materials")
					.setAuthor("Miner Shop", null, event.getSelfUser().getEffectiveAvatarUrl())
					.setTitle("Page " + page.getPage() + "/" + page.getMaxPage())
					.setFooter(PagedResult.DEFAULT_FOOTER_TEXT);

				page.forEach((miner, index) -> embed.addField(miner.getName(), String.format("Price: $%,d", miner.getPrice()), true));

				return new MessageBuilder().setEmbeds(embed.build()).build();
			});

		paged.execute(event);
	}

	@Command(value="buy", description="Buy a miner from the `miner shop`")
	@CommandId(403)
	@Examples({"miner buy 10 Platinum Miner", "miner buy Platinum", "miner buy Platinum 5"})
	public void buy(Sx4CommandEvent event, @Argument(value="miners", endless=true) ItemStack<Miner> stack) {
		long amount = stack.getAmount();
		if (amount < 1) {
			event.replyFailure("You need to buy at least 1 miner").queue();
			return;
		}

		long price = stack.getTotalPrice();
		Miner miner = stack.getItem();

		event.getMongo().withTransaction(session -> {
			UpdateResult result = event.getMongo().getUsers().updateOne(session, Filters.eq("_id", event.getAuthor().getIdLong()), List.of(EconomyUtility.decreaseBalanceUpdate(price)));
			if (result.getModifiedCount() == 0) {
				event.replyFormat("You do not have **$%,d** %s", price, event.getConfig().getFailureEmote()).queue();
				session.abortTransaction();
				return;
			}

			Bson filter = Filters.and(
				Filters.eq("userId", event.getAuthor().getIdLong()),
				Filters.eq("item.id", miner.getId())
			);

			List<Bson> update = List.of(
				Operators.set("item", miner.toData()),
				Operators.set("amount", Operators.add(Operators.ifNull("$amount", 0L), amount))
			);

			event.getMongo().getItems().updateOne(session, filter, update, new UpdateOptions().upsert(true));
		}).whenComplete((updated, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception) || !updated) {
				return;
			}

			event.replyFormat("You just bought `%,d %s` for **$%,d** %s", amount, miner.getName(), price, event.getConfig().getSuccessEmote()).queue();
		});
	}

	@Command(value="collect", description="Collect your materials from your miners")
	@CommandId(404)
	@Examples({"miner collect"})
	@BotPermissions(permissions=Permission.MESSAGE_EMBED_LINKS)
	public void collect(Sx4CommandEvent event) {
		event.getMongo().withTransaction(session -> {
			Bson filter = Filters.and(
				Filters.eq("userId", event.getAuthor().getIdLong()),
				Filters.eq("item.type", ItemType.MINER.getId())
			);

			List<Document> miners = event.getMongo().getItems().find(session, filter).projection(Projections.include("amount", "item.id", "resets")).into(new ArrayList<>());
			if (miners.isEmpty()) {
				event.replyFailure("You do not have any miners").queue();
				session.abortTransaction();
				return null;
			}

			EconomyManager manager = event.getBot().getEconomyManager();

			long total = 0, usableTotal = 0, lowestReset = Long.MAX_VALUE;
			Map<Material, Long> materials = new HashMap<>();

			for (Document data : miners) {
				CooldownItemStack<Miner> stack = new CooldownItemStack<>(event.getBot().getEconomyManager(), data);
				Miner miner = stack.getItem();

				long nextReset = stack.getTimeRemaining();
				lowestReset = Math.min(nextReset, lowestReset);

				long amount = stack.getUsableAmount();
				if (amount == 0) {
					continue;
				}

				usableTotal += amount;

				for (Material material : manager.getItems(Material.class)) {
					if (material.isHidden()) {
						continue;
					}

					double randomDouble = 0.85D + manager.getRandom().nextDouble() * (1D - 0.85D);

					long materialAmount = Math.round((amount / Math.ceil((material.getPrice() / 10D) * miner.getMultiplier())) * miner.getMaxMaterials() * randomDouble);
					if (materialAmount == 0) {
						continue;
					}

					total += materialAmount;
					materials.compute(material, (key, value) -> value == null ? materialAmount : value + materialAmount);
				}

				event.getMongo().getItems().updateOne(Filters.and(Filters.eq("userId", event.getAuthor().getIdLong()), Filters.eq("item.id", miner.getId())), List.of(EconomyUtility.getResetsUpdate(amount, MinerCommand.COOLDOWN)));
			}

			if (usableTotal == 0) {
				event.reply("Slow down! You can collect from your miner in " + TimeUtility.getTimeString(lowestReset) + " :stopwatch:").queue();
				session.abortTransaction();
				return null;
			}

			StringJoiner content = new StringJoiner("\n");
			List<WriteModel<Document>> bulkData = new ArrayList<>();

			List<Material> materialKeys = materials.keySet().stream().sorted(Comparator.comparingLong(materials::get).reversed()).collect(Collectors.toList());
			for (Material material : materialKeys) {
				long amount = materials.get(material);

				Bson materialFilter = Filters.and(
					Filters.eq("userId", event.getAuthor().getIdLong()),
					Filters.eq("item.id", material.getId())
				);

				List<Bson> update = List.of(
					Operators.set("item", material.toData()),
					Operators.set("amount", Operators.add(Operators.ifNull("$amount", 0L), amount))
				);

				bulkData.add(new UpdateOneModel<>(materialFilter, update, new UpdateOptions().upsert(true)));

				content.add(String.format("• %,d %s %s", amount, material.getName(), material.getEmote()));
			}

			if (!bulkData.isEmpty()) {
				event.getMongo().getItems().bulkWrite(session, bulkData);
			}

			EmbedBuilder embed = new EmbedBuilder()
				.setAuthor(event.getAuthor().getName(), null, event.getAuthor().getEffectiveAvatarUrl())
				.setColor(event.getMember().getColor())
				.setDescription(String.format("You used your miners and gathered **%,d** material%s%s", total, total == 1 ? "" : "s", total == 0 ? "" : "\n\n" + content.toString()));

			return embed.build();
		}).whenComplete((embed, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception) || embed == null) {
				return;
			}

			event.reply(embed).queue();
		});
	}

}
