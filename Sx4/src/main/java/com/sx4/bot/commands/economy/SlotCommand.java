package com.sx4.bot.commands.economy;

import com.jockie.bot.core.argument.Argument;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.ReturnDocument;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.database.mongo.MongoDatabase;
import com.sx4.bot.database.mongo.model.Operators;
import com.sx4.bot.entities.argument.AmountArgument;
import com.sx4.bot.entities.economy.Slot;
import com.sx4.bot.entities.games.GameState;
import com.sx4.bot.entities.games.GameType;
import com.sx4.bot.utility.ExceptionUtility;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.util.List;

public class SlotCommand extends Sx4Command {

	public SlotCommand() {
		super("slot", 408);

		super.setDescription("Bet some of your money in the slots and hope you hit the jackpot");
		super.setAliases("slots");
		super.setExamples("slot 1000", "slot 10%", "slot all");
		super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
		super.setCategoryAll(ModuleCategory.ECONOMY);
	}

	public void onCommand(Sx4CommandEvent event, @Argument(value="bet") AmountArgument amount) {
		Slot[] slots = Slot.getSlots(event.getBot().getEconomyManager().getRandom());
		Slot firstSlot = slots[0], secondSlot = slots[1], thirdSlot = slots[2];

		boolean won = firstSlot == secondSlot && firstSlot == thirdSlot;

		Document betData = new Document("bet", amount.hasDecimal() ? Operators.toLong(Operators.ceil(Operators.multiply(amount.getDecimal(), "$$balance"))) : amount.getAmount());

		List<Bson> update = List.of(
			Operators.set("economy.winnings", Operators.let(new Document("winnings", Operators.ifNull("$economy.winnings", 0L)), Operators.let(betData, Operators.cond(Operators.lt(Operators.ifNull("$economy.balance", 0L), "$$bet"), "$$winnings", Operators.cond(won, Operators.add("$$winnings", Operators.subtract(Operators.toLong(Operators.round(Operators.multiply("$$bet", firstSlot.getMultiplier()))), "$$bet")), Operators.subtract("$$winnings", "$$bet")))))),
			Operators.set("economy.balance", Operators.let(new Document("balance", Operators.ifNull("$economy.balance", 0L)), Operators.let(betData, Operators.cond(Operators.lt("$$balance", "$$bet"), "$$balance", Operators.cond(won, Operators.add("$$balance", Operators.subtract(Operators.toLong(Operators.round(Operators.multiply("$$bet", firstSlot.getMultiplier()))), "$$bet")), Operators.subtract("$$balance", "$$bet"))))))
		);

		FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().returnDocument(ReturnDocument.BEFORE).projection(Projections.include("economy.balance")).upsert(true);
		event.getMongo().findAndUpdateUserById(event.getAuthor().getIdLong(), update, options).whenComplete((data, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			if (data == null) {
				event.replyFailure("You do not have any money").queue();
				return;
			}

			long balance = data.getEmbedded(List.of("economy", "balance"), 0L);
			if (balance == 0L) {
				event.replyFailure("You do not have any money").queue();
				return;
			}

			long bet = amount.getEffectiveAmount(balance);
			if (balance < bet) {
				event.replyFormat("You do not have **$%,d** %s", bet, event.getConfig().getFailureEmote()).queue();
				return;
			}

			long winnings = Math.round(bet * firstSlot.getMultiplier());

			EmbedBuilder embed = new EmbedBuilder()
				.setAuthor("🎰 Slot Machine 🎰")
				.setFooter(event.getAuthor().getAsTag(), event.getAuthor().getEffectiveAvatarUrl())
				.setThumbnail("https://images.emojiterra.com/twitter/512px/1f3b0.png")
				.setDescription(firstSlot.getAbove().getEmote() + secondSlot.getAbove().getEmote() + thirdSlot.getAbove().getEmote() + "\n" +
					firstSlot.getEmote() + secondSlot.getEmote() + thirdSlot.getEmote() + "\n" +
					firstSlot.getBelow().getEmote() + secondSlot.getBelow().getEmote() + thirdSlot.getBelow().getEmote());

			embed.appendDescription(String.format("\n\nYou " + (won ? "won" : "lost") + " **$%,d**!", won ? winnings : bet));

			event.reply(embed.build()).queue();

			Document gameData = new Document("userId", event.getAuthor().getIdLong())
				.append("slots", List.of(firstSlot.name(), secondSlot.name(), thirdSlot.name()))
				.append("bet", bet)
				.append("winnings", won ? winnings - bet : -bet)
				.append("state", won ? GameState.WIN.getId() : GameState.LOSS.getId())
				.append("gameId", ObjectId.get())
				.append("type", GameType.SLOT.getId());

			event.getMongo().insertGame(gameData).whenComplete(MongoDatabase.exceptionally(event));
		});
	}

}
