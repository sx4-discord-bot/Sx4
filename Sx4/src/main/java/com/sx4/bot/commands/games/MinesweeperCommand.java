package com.sx4.bot.commands.games;

import com.jockie.bot.core.argument.Argument;
import com.sx4.bot.annotations.argument.DefaultInt;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import net.dv8tion.jda.api.entities.Message;

import java.util.HashMap;
import java.util.Map;

public class MinesweeperCommand extends Sx4Command {

	public enum MinesweeperType {
		ZERO(0, ":zero:"),
		ONE(1, ":one:"),
		TWO(2, ":two:"),
		THREE(3, ":three:"),
		FOUR(4, ":four:"),
		FIVE(5, ":five:"),
		SIX(6, ":six:"),
		SEVEN(7, ":seven:"),
		EIGHT(8, ":eight:"),
		BOMB(9, ":bomb:"),
		UNKNOWN(-1, "");

		private int number;
		private String emote;

		private MinesweeperType(int number, String emote) {
			this.number = number;
			this.emote = emote;
		}

		public int getNumber() {
			return this.number;
		}

		public String getEmote() {
			return this.emote;
		}

		public static MinesweeperType fromNumber(int number) {
			for (MinesweeperType minesweeper : MinesweeperType.values()) {
				if (minesweeper.getNumber() == number) {
					return minesweeper;
				}
			}

			return MinesweeperType.UNKNOWN;
		}
	}

	public MinesweeperCommand() {
		super("minesweeper");

		super.setDescription("Play mineweeper within discord");
		super.setExamples("minesweeper", "minesweeper 12", "minesweeper 5 5 5");
		super.setCooldownDuration(5);
	}

	public void onCommand(Sx4CommandEvent event, @Argument(value="bombs") @DefaultInt(10) int bombs, @Argument(value="grid x") @DefaultInt(10) int gridX, @Argument(value="grid y") @DefaultInt(10) int gridY) {
		if (gridX < 2 || gridY < 2) {
			event.replyFailure("The grid has to be at least 2x2 in size").queue();
			return;
		}

		int maxBombs = gridX * gridY - 1;
		if (bombs > maxBombs) {
			event.replyFormat("**%,d** is the max amount of bombs you can have in this grid %s", maxBombs, this.config.getFailureEmote()).queue();
			return;
		}

		if (bombs < 1) {
			event.replyFailure("You need at least 1 bomb to play").queue();
			return;
		}

		Map<Integer, Map<Integer, MinesweeperType>> positions = new HashMap<>();
		for (int i = 0; i < bombs; i++) {
			Map<Integer, MinesweeperType> position;
			int x, y;
			do {
				x = this.random.nextInt(gridX);
				y = this.random.nextInt(gridY);

				position = positions.computeIfAbsent(x, key -> new HashMap<>());
			} while (position.containsKey(y));

			position.put(y, MinesweeperType.BOMB);
		}

		for (int x = 0; x < gridX; x++) {
			for (int y = 0; y < gridY; y++) {
				MinesweeperType current = positions.getOrDefault(x, new HashMap<>())
					.getOrDefault(y, MinesweeperType.UNKNOWN);

				if (current == MinesweeperType.BOMB) {
					continue;
				}

				int amount = 0;
				for (int aroundX = -1; aroundX < 2; aroundX++) {
					for (int aroundY = -1; aroundY < 2; aroundY++) {
						MinesweeperType type = positions.getOrDefault(aroundX + x, new HashMap<>())
							.getOrDefault(aroundY + y, MinesweeperType.UNKNOWN);

						if (type == MinesweeperType.BOMB) {
							amount++;
						}
					}
				}

				positions.computeIfAbsent(x, key -> new HashMap<>())
					.put(y, MinesweeperType.fromNumber(amount));
			}
		}

		StringBuilder result = new StringBuilder();
		for (int x = 0; x < gridX; x++) {
			for (int y = 0; y < gridY; y++) {
				MinesweeperType current = positions.get(x).get(y);

				result.append("||").append(current.getEmote()).append("||");
			}

			result.append("\n");
		}

		if (result.length() > Message.MAX_CONTENT_LENGTH) {
			event.replyFailure("That grid size is too big to show").queue();
			return;
		}

		event.reply(result.toString()).queue();
	}

}
