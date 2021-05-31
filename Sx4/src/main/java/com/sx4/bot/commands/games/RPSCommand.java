package com.sx4.bot.commands.games;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.command.Command;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.sx4.bot.annotations.argument.Lowercase;
import com.sx4.bot.annotations.argument.Options;
import com.sx4.bot.annotations.command.CommandId;
import com.sx4.bot.annotations.command.Examples;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.database.mongo.MongoDatabase;
import com.sx4.bot.entities.games.GameState;
import com.sx4.bot.entities.games.GameType;
import com.sx4.bot.utility.NumberUtility;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RPSCommand extends Sx4Command {

	private final Map<Integer, String> emotes = new HashMap<>();
	private final Map<String, Integer> responses = new HashMap<>();
	{
		this.responses.put("rock", 0);
		this.responses.put("paper", 1);
		this.responses.put("scissors", 2);

		this.emotes.put(0, ":rock:");
		this.emotes.put(1, ":page_facing_up:");
		this.emotes.put(2, ":scissors:");
	}

	public RPSCommand() {
		super("rps", 295);

		super.setDescription("Play rock paper scissors versus the bot");
		super.setAliases("rock paper scissors");
		super.setExamples("rps rock", "rps scissors", "rps paper");
		super.setCategoryAll(ModuleCategory.GAMES);
	}

	public void onCommand(Sx4CommandEvent event, @Argument(value="choice") @Options({"rock", "paper", "scissors"}) @Lowercase String choice) {
		int choiceInt = this.responses.get(choice), botChoice = event.getRandom().nextInt(3);

		ObjectId gameId = ObjectId.get();

		Document authorData = new Document("userId", event.getAuthor().getIdLong())
			.append("choice", choiceInt)
			.append("gameId", gameId)
			.append("type", GameType.ROCK_PAPER_SCISSORS.getId());

		Document opponentData = new Document("userId", event.getSelfUser().getIdLong())
			.append("choice", botChoice)
			.append("gameId", gameId)
			.append("type", GameType.ROCK_PAPER_SCISSORS.getId());

		StringBuilder outcome = new StringBuilder(event.getAuthor().getName() + ": " + this.emotes.get(choiceInt) + "\n" + event.getSelfUser().getName() + ": " + this.emotes.get(botChoice) + "\n\n");
		if (choiceInt == botChoice) {
			outcome.append("Draw, let's go again!");

			authorData.append("state", GameState.DRAW.getId());
			opponentData.append("state", GameState.DRAW.getId());
		} else if ((botChoice == 2 && choiceInt == 0) || choiceInt - 1 == botChoice) {
			outcome.append("You win, congratulations :trophy:");

			authorData.append("state", GameState.WIN.getId());
			opponentData.append("state", GameState.LOSS.getId());
		} else {
			outcome.append("You lose, better luck next time.");

			authorData.append("state", GameState.LOSS.getId());
			opponentData.append("state", GameState.WIN.getId());
		}

		event.reply(outcome).queue();

		event.getMongo().insertManyGames(List.of(authorData, opponentData)).whenComplete(MongoDatabase.exceptionally(event.getShardManager()));
	}

	@Command(value="stats", description="View some stats about your personal rps record")
	@CommandId(296)
	@Examples({"rps stats", "rps stats @Shea#6653"})
	public void stats(Sx4CommandEvent event, @Argument(value="user", endless=true, nullDefault=true) Member member) {
		User user = member == null ? event.getAuthor() : member.getUser();

		Bson filter = Filters.and(Filters.eq("userId", user.getIdLong()), Filters.eq("type", GameType.ROCK_PAPER_SCISSORS.getId()));

		List<Document> games = event.getMongo().getGames(filter, Projections.include("state")).into(new ArrayList<>());
		if (games.isEmpty()) {
			event.replyFailure("That user has not played rock paper scissors yet").queue();
			return;
		}

		int wins = 0, draws = 0, losses = 0, total = 0;
		for (Document game : games) {
			GameState state = GameState.fromId(game.getInteger("state"));
			if (state == GameState.WIN) {
				wins++;
			} else if (state == GameState.DRAW) {
				draws++;
			} else if (state == GameState.LOSS) {
				losses++;
			}

			total++;
		}

		EmbedBuilder embed = new EmbedBuilder()
			.setAuthor(user.getAsTag(), null, user.getEffectiveAvatarUrl())
			.setDescription(String.format("Wins: %,d\nDraws: %,d\nLosses: %,d\n\nWin Percentage: %s%%", wins, draws, losses, NumberUtility.DEFAULT_DECIMAL_FORMAT.format(((double) wins / total) * 100)));

		event.reply(embed.build()).queue();
	}

}
