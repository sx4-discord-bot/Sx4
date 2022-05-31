package com.sx4.bot.entities.games;

import com.sx4.bot.core.Sx4;
import com.sx4.bot.database.mongo.MongoDatabase;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.sharding.ShardManager;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.security.SecureRandom;
import java.util.List;

public class GuessTheNumberGame {

	private final SecureRandom random = new SecureRandom();

	private final Sx4 bot;

	private final long messageId;

	private final int min, max;

	private final long opponentId;
	private final long userId;

	private int opponentGuess = -1;
	private int userGuess = -1;

	public GuessTheNumberGame(Sx4 bot, long messageId, long opponentId, long userId, int min, int max) {
		this.bot = bot;
		this.messageId = messageId;
		this.opponentId = opponentId;
		this.userId = userId;
		this.min = min;
		this.max = max;
	}

	public long getOpponentId() {
		return this.opponentId;
	}

	public long getUserId() {
		return this.userId;
	}

	public int getMin() {
		return this.min;
	}

	public int getMax() {
		return this.max;
	}

	public long getMessageId() {
		return this.messageId;
	}

	public void setGuess(InteractionHook hook, long id, int guess) {
		if (id == this.opponentId) {
			this.opponentGuess = guess;
		} else if (id == this.userId) {
			this.userGuess = guess;
		}

		if (this.opponentGuess != -1 && this.userGuess != -1) {
			this.endGame(hook);
		}
	}

	public void endGame(InteractionHook hook) {
		this.bot.getGuessTheNumberManager().removeGame(this);

		ShardManager manager = this.bot.getShardManager();

		ObjectId gameId = ObjectId.get();

		int randomNumber = this.random.nextInt(this.max) + 1;

		Document authorData = new Document("userId", this.userId)
			.append("gameId", gameId)
			.append("type", GameType.GUESS_THE_NUMBER.getId())
			.append("choice", this.userGuess)
			.append("answer", randomNumber);

		Document opponentData = new Document("userId", this.opponentId)
			.append("gameId", gameId)
			.append("type", GameType.GUESS_THE_NUMBER.getId())
			.append("choice", this.opponentGuess)
			.append("answer", randomNumber);

		int authorDifference = Math.abs(this.userGuess - randomNumber), opponentDifference = Math.abs(this.opponentGuess - randomNumber);

		User user = manager.getUserById(this.userId), opponent = manager.getUserById(this.opponentId);

		StringBuilder content = new StringBuilder("The random number was **" + randomNumber + "**\n" + (opponent == null ? this.opponentId : opponent.getName()) + "'s number was **" + this.opponentGuess + "**\n" + (user == null ? this.userId : user.getName()) + "'s number was **" + this.userGuess + "**\n\n");
		if (authorDifference == opponentDifference) {
			content.append("You both guessed the same number, It was a draw!");

			authorData.append("state", GameState.DRAW.getId());
			opponentData.append("state", GameState.DRAW.getId());
		} else if (authorDifference > opponentDifference) {
			content.append(opponent == null ? this.opponentId : opponent.getName()).append(" won! They were the closest to ").append(randomNumber);

			authorData.append("state", GameState.LOSS.getId());
			opponentData.append("state", GameState.WIN.getId());
		} else {
			content.append(user == null ? this.userId : user.getName()).append(" won! They were the closest to ").append(randomNumber);

			authorData.append("state", GameState.WIN.getId());
			opponentData.append("state", GameState.LOSS.getId());
		}

		hook.sendMessage(content.toString()).queue();
		hook.editMessageComponentsById(this.messageId, ActionRow.of(Button.primary("a", "Guess the number").asDisabled())).queue();

		this.bot.getMongo().insertManyGames(List.of(authorData, opponentData)).whenComplete(MongoDatabase.exceptionally());
	}

}
