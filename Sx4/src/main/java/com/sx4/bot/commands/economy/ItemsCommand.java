package com.sx4.bot.commands.economy;

import com.jockie.bot.core.argument.Argument;
import com.mongodb.client.model.*;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.entities.economy.item.ItemStack;
import com.sx4.bot.entities.economy.item.ItemType;
import com.sx4.bot.utility.ExceptionUtility;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.*;

public class ItemsCommand extends Sx4Command {

	public ItemsCommand() {
		super("items", 356);

		super.setDescription("View the items a user has");
		super.setAliases("inventory");
		super.setExamples("items", "items @Shea#6653", "items Shea");
		super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
		super.setCategoryAll(ModuleCategory.ECONOMY);
	}

	public void onCommand(Sx4CommandEvent event, @Argument(value="user", endless=true, nullDefault=true) Member member) {
		Member effectiveMember = member == null ? event.getMember() : member;

		List<Bson> usersPipeline = List.of(
			Aggregates.project(Projections.include("economy.balance")),
			Aggregates.match(Filters.eq("_id", effectiveMember.getIdLong())),
			Aggregates.group("balance", Accumulators.sum("balance", "$economy.balance"))
		);

		List<Bson> pipeline = List.of(
			Aggregates.match(Filters.and(Filters.eq("userId", effectiveMember.getIdLong()), Filters.ne("amount", 0))),
			Aggregates.group("$item.name", Accumulators.first("item", "$item"), Accumulators.first("amount", "$amount"), Accumulators.first("type", "$item.type")),
			Aggregates.sort(Sorts.descending("amount")),
			Aggregates.unionWith("users", usersPipeline)
		);

		event.getMongo().aggregateItems(pipeline).whenComplete((data, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			EmbedBuilder embed = new EmbedBuilder()
				.setAuthor("Items", null, effectiveMember.getUser().getEffectiveAvatarUrl())
				.setColor(effectiveMember.getColorRaw());

			List<Document> items = data.into(new ArrayList<>());
			if (items.size() == 1) {
				event.replyFailure("That user does not have any items").queue();
				return;
			}

			StringBuilder footer = new StringBuilder("If a category isn't shown it means you have no items in that category | Balance: $");

			Map<ItemType, StringJoiner> types = new HashMap<>();
			for (Document item : items) {
				String name = item.getString("_id");
				if (name.equals("balance")) {
					footer.append(String.format("%,d", item.get("balance", Number.class).longValue()));
					continue;
				}

				ItemType type = ItemType.fromId(item.getInteger("type"));
				ItemStack<?> stack = new ItemStack<>(event.getBot().getEconomyManager(), item);

				types.compute(type, (key, value) -> (value == null ? new StringJoiner("\n") : value).add(stack.toString()));
			}

			types.forEach((type, joiner) -> embed.addField(type.getName(), joiner.toString(), true));
			embed.setFooter(footer.toString());

			event.reply(embed.build()).queue();
		});
	}

}
