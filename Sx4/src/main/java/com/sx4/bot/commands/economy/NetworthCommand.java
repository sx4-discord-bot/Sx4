package com.sx4.bot.commands.economy;

import com.jockie.bot.core.argument.Argument;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.database.mongo.model.Operators;
import com.sx4.bot.utility.ExceptionUtility;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.List;

public class NetworthCommand extends Sx4Command {

	public NetworthCommand() {
		super("networth", 384);

		super.setDescription("View the networth of a user");
		super.setAliases("net");
		super.setExamples("networth", "networth @Shea#6653", "networth Shea");
		super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
		super.setCategoryAll(ModuleCategory.ECONOMY);
	}

	public void onCommand(Sx4CommandEvent event, @Argument(value="user", endless=true, nullDefault=true) Member member) {
		boolean self = member == null;
		Member effectiveMember = self ? event.getMember() : member;
		User user = self ? event.getAuthor() : member.getUser();

		List<Bson> userPipeline = List.of(
			Aggregates.project(Projections.computed("networth", "$economy.balance")),
			Aggregates.match(Filters.eq("_id", effectiveMember.getIdLong()))
		);

		List<Bson> pipeline = List.of(
			Aggregates.project(Projections.fields(Projections.computed("_id", "$userId"), Projections.computed("networth", Operators.cond(Operators.exists("$item.currentDurability"), Operators.toLong(Operators.multiply(Operators.divide("$item.price", "$item.maxDurability"), "$item.currentDurability")), Operators.multiply("$item.price", "$amount"))))),
			Aggregates.match(Filters.eq("_id", effectiveMember.getIdLong())),
			Aggregates.unionWith("users", userPipeline),
			Aggregates.group(null, Accumulators.sum("networth", "$networth"))
		);

		event.getMongo().aggregateItems(pipeline).whenComplete((iterable, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			Document data = iterable.first();

			EmbedBuilder embed = new EmbedBuilder()
				.setAuthor(user.getName(), null, user.getEffectiveAvatarUrl())
				.setColor(effectiveMember.getColorRaw())
				.setDescription((self ? "Your" : "Their") + String.format(" networth: **$%,d**", data == null ? 0L : data.getLong("networth")));

			event.reply(embed.build()).queue();
		});
	}

}
