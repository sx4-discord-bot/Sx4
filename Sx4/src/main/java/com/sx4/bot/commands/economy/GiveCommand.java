package com.sx4.bot.commands.economy;

import com.jockie.bot.core.argument.Argument;
import com.mongodb.client.model.*;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.entities.argument.AmountArgument;
import com.sx4.bot.utility.EconomyUtility;
import com.sx4.bot.utility.ExceptionUtility;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import org.bson.Document;

import java.util.List;

public class GiveCommand extends Sx4Command {

	public GiveCommand() {
		super("give", 398);

		super.setDescription("Give other users some money, be generous");
		super.setExamples("give @Shea#6653 all", "give Shea 50%", "give 402557516728369153 1000");
		super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
		super.setCategoryAll(ModuleCategory.ECONOMY);
	}

	public void onCommand(Sx4CommandEvent event, @Argument(value="user") Member member, @Argument(value="amount") AmountArgument amount) {
		User user = member == null ? event.getAuthor() : member.getUser();
		if (user.isBot() && user.getIdLong() != event.getSelfUser().getIdLong()) {
			event.replyFailure("You can not give money to bots").queue();
			return;
		}

		boolean tax = user.getIdLong() == event.getSelfUser().getIdLong();

		event.getMongo().withTransaction(session -> {
			FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().projection(Projections.include("economy.balance")).returnDocument(ReturnDocument.BEFORE);

			Document authorData = event.getMongo().getUsers().findOneAndUpdate(session, Filters.eq("_id", event.getAuthor().getIdLong()), List.of(EconomyUtility.decreaseBalanceUpdate(amount)), options);
			if (authorData == null) {
				event.replyFailure("You do not have any money").queue();
				session.abortTransaction();
				return null;
			}

			long authorBalance = authorData.getEmbedded(List.of("economy", "balance"), 0L);
			if (authorBalance == 0L) {
				event.replyFailure("You do not have any money").queue();
				session.abortTransaction();
				return null;
			}

			long effectiveAmount = amount.getEffectiveAmount(authorBalance);
			if (authorBalance < effectiveAmount) {
				event.replyFailure(String.format("You do not have **$%,d**", authorBalance)).queue();
				session.abortTransaction();
				return null;
			}

			long amountGiven = tax ? effectiveAmount : (long) (effectiveAmount * 0.95D), taxAmount = tax ? effectiveAmount : (long) Math.ceil(effectiveAmount * 0.05D);

			Document userData;
			if (tax) {
				userData = event.getMongo().getUsers().findOneAndUpdate(session, Filters.eq("_id", event.getSelfUser().getIdLong()), Updates.inc("economy.balance", amountGiven), options);
			} else {
				userData = event.getMongo().getUsers().findOneAndUpdate(session, Filters.eq("_id", user.getIdLong()), Updates.inc("economy.balance", amountGiven), options.upsert(true));

				event.getMongo().getUsers().updateOne(session, Filters.eq("_id", event.getSelfUser().getIdLong()), Updates.inc("economy.balance", taxAmount));
			}

			long userBalance = userData == null ? 0L : userData.getEmbedded(List.of("economy", "balance"), 0L);

			EmbedBuilder embed = new EmbedBuilder()
				.setAuthor(event.getAuthor().getName() + " → " + user.getName(), null, "https://cdn0.iconfinder.com/data/icons/social-messaging-ui-color-shapes/128/money-circle-green-3-512.png")
				.setColor(event.getMember().getColor())
				.setDescription(String.format("You have gifted **$%,d** to **%s**\n\n%s's new balance: **$%,d**\n%s's new balance: **$%,d**", amountGiven, user.getName(), event.getAuthor().getName(), authorBalance - effectiveAmount, user.getName(), userBalance + amountGiven))
				.setFooter(String.format("$%,d (%d%%) tax was taken", taxAmount, Math.round((double) taxAmount / effectiveAmount * 100)), null);

			return embed.build();
		}).whenComplete((embed, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			if (embed == null) {
				return;
			}

			event.reply(embed).queue();
		});
	}

}
