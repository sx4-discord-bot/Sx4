package com.sx4.bot.commands.economy;

import com.jockie.bot.core.argument.Argument;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.ReturnDocument;
import com.sx4.bot.annotations.argument.Limit;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.database.mongo.MongoDatabase;
import com.sx4.bot.database.mongo.model.Operators;
import com.sx4.bot.entities.argument.AmountArgument;
import com.sx4.bot.entities.games.GameState;
import com.sx4.bot.entities.games.GameType;
import com.sx4.bot.utility.ExceptionUtility;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.util.List;

public class RussianRouletteCommand extends Sx4Command {

	public RussianRouletteCommand() {
		super("russian roulette", 393);

		super.setDescription("Put a gun to your head and choose how many bullets go in the chamber if you're shot you lose your bet if you win you gain your winnings and certain amount depending on how many bullets you put in the chamber");
		super.setAliases("rusr", "russianroulette", "roulette");
		super.setExamples("russian roulette 3 100", "russian roulette 1 10%", "russian roulette 5 all");
		super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
		super.setCategoryAll(ModuleCategory.ECONOMY);
	}

	public void onCommand(Sx4CommandEvent event, @Argument(value="bullets") @Limit(min=1, max=5) int bullets, @Argument(value="money") AmountArgument amount) {
		EmbedBuilder embed = new EmbedBuilder()
			.setAuthor(event.getAuthor().getName(), null, event.getAuthor().getEffectiveAvatarUrl())
			.setColor(event.getMember().getColor());

		int bulletIndex = event.getBot().getEconomyManager().getRandom().nextInt(6);
		boolean won = bullets - 1 < bulletIndex;

		Document betData = new Document("bet", amount.hasDecimal() ? Operators.toLong(Operators.ceil(Operators.multiply(amount.getDecimal(), "$$balance"))) : amount.getAmount());
		Bson winningsData = Operators.subtract(Operators.toLong(Operators.ceil(Operators.divide(Operators.multiply(5.7D, "$$bet"), 6 - bullets))), "$$bet");

		List<Bson> update = List.of(
			Operators.set("economy.winnings", Operators.let(new Document("balance", Operators.ifNull("$economy.balance", 0L)).append("winnings", Operators.ifNull("$economy.winnings", 0L)), Operators.let(betData, Operators.cond(Operators.or(Operators.lt("$$bet", 20), Operators.lt("$$balance", "$$bet")), "$$winnings", Operators.cond(won, Operators.add("$$winnings", winningsData), Operators.subtract("$$winnings", "$$bet")))))),
			Operators.set("economy.balance", Operators.let(new Document("balance", Operators.ifNull("$economy.balance", 0L)), Operators.let(betData, Operators.cond(Operators.or(Operators.lt("$$bet", 20), Operators.lt("$$balance", "$$bet")), "$$balance", Operators.cond(won, Operators.add("$$balance", winningsData), Operators.subtract("$$balance", "$$bet"))))))
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
			if (bet < 20) {
				event.replyFailure("Your bet has to be at least **$20**").queue();
				return;
			}

			if (balance < bet) {
				event.replyFormat("You do not have **$%,d** %s", bet, event.getConfig().getFailureEmote()).queue();
				return;
			}

			long winnings = (long) Math.ceil((5.7D * bet) / (6 - bullets));
			if (won) {
				embed.setDescription(String.format("You're lucky, you get to live another day.\nYou won **$%,d**", winnings));
			} else {
				embed.setDescription(String.format("You were shot :gun:\nYou lost your bet of **$%,d**", bet));
			}

			event.reply(embed.build()).queue();

			Document gameData = new Document("userId", event.getAuthor().getIdLong())
				.append("bullets", bullets)
				.append("bullet", bulletIndex + 1)
				.append("bet", bet)
				.append("winnings", won ? winnings - bet : -bet)
				.append("state", won ? GameState.WIN.getId() : GameState.LOSS.getId())
				.append("gameId", ObjectId.get())
				.append("type", GameType.RUSSIAN_ROULETTE.getId());

			event.getMongo().insertGame(gameData).whenComplete(MongoDatabase.exceptionally(event));
		});
	}

}
