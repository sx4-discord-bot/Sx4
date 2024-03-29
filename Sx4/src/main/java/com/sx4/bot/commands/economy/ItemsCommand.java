package com.sx4.bot.commands.economy;

import com.jockie.bot.core.argument.Argument;
import com.mongodb.client.model.*;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.database.mongo.model.Operators;
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
			Aggregates.match(Filters.eq("_id", effectiveMember.getIdLong())),
			Aggregates.project(Projections.computed("balance", Operators.ifNull("$economy.balance", 0L))),
			Aggregates.addFields(new Field<>("name", "balance"))
		);

		List<Bson> pipeline = List.of(
			Aggregates.match(Filters.and(Filters.eq("userId", effectiveMember.getIdLong()), Filters.ne("amount", 0))),
			Aggregates.project(Projections.fields(Projections.computed("name", "$item.name"), Projections.computed("type", "$item.type"), Projections.include("item", "amount"))),
			Aggregates.sort(Sorts.descending("amount")),
			Aggregates.unionWith("users", usersPipeline)
		);

		event.getMongo().aggregateItems(pipeline).whenComplete((items, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			EmbedBuilder embed = new EmbedBuilder()
				.setAuthor("Items", null, effectiveMember.getUser().getEffectiveAvatarUrl())
				.setColor(effectiveMember.getColorRaw());

			if (items.isEmpty()) {
				event.replyFailure("That user does not have any items").queue();
				return;
			}

			String footerText = "If a category isn't shown it means you have no items in that category | Balance: $";
			StringBuilder footer = new StringBuilder(footerText);

			Map<ItemType, StringJoiner> types = new HashMap<>();
			for (Document item : items) {
				String name = item.getString("name");
				if (name.equals("balance")) {
					if (items.size() == 1) {
						event.replyFailure("That user does not have any items").queue();
						return;
					}

					footer.append(String.format("%,d", item.get("balance", 0L)));
					continue;
				}

				ItemType type = ItemType.fromId(item.getInteger("type"));
				ItemStack<?> stack = new ItemStack<>(event.getBot().getEconomyManager(), item);

				types.compute(type, (key, value) -> (value == null ? new StringJoiner("\n") : value).add(stack.toString()));
			}

			if (footer.length() == footerText.length()) {
				footer.append("0");
			}

			types.forEach((type, joiner) -> embed.addField(type.getName(), joiner.toString(), true));
			embed.setFooter(footer.toString());

			event.reply(embed.build()).queue();
		});
	}

}
