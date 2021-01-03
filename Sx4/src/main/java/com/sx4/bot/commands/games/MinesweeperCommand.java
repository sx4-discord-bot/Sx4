package com.sx4.bot.commands.games;

import com.jockie.bot.core.argument.Argument;
import com.sx4.bot.annotations.argument.DefaultNumber;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;

import java.util.HashMap;
import java.util.Map;

public class MinesweeperCommand extends Sx4Command {

	public enum MinesweeperType {
		ZERO(0, "0️⃣"),
		ONE(1, "1️⃣"),
		TWO(2, "2️⃣"),
		THREE(3, "3️⃣"),
		FOUR(4, "4️⃣"),
		FIVE(5, "5️⃣"),
		SIX(6, "6️⃣"),
		SEVEN(7, "7️⃣"),
		EIGHT(8, "8️⃣"),
		BOMB(9, "\uD83D\uDCA3"),
		UNKNOWN(-1, "");

		private final int number;
		private final String emote;

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
		super("minesweeper", 1);

		super.setDescription("Play mineweeper within discord");
		super.setExamples("minesweeper", "minesweeper 12", "minesweeper 5 5 5");
		super.setCooldownDuration(5);
		super.setCategoryAll(ModuleCategory.GAMES);
	}

	public void onCommand(Sx4CommandEvent event, @Argument(value="bombs") @DefaultNumber(10) int bombs, @Argument(value="grid x") @DefaultNumber(10) int gridX, @Argument(value="grid y") @DefaultNumber(10) int gridY) {
		int gridSize = gridX * gridY;
		if (gridSize < 4) {
			event.replyFailure("The grid has to be at least 4 blocks in size").queue();
			return;
		}

		if (bombs > gridSize / 2) {
			event.replyFailure("Only 50% of the grid can be bombs").queue();
			return;
		}

		if (bombs < 1) {
			event.replyFailure("You need at least 1 bomb to play").queue();
			return;
		}

		if (bombs * 6 + (gridSize - bombs) * 7 + (gridY - 1) > 1400) {
			event.replyFailure("That grid size is too big to show").queue();
			return;
		}

		Map<Integer, Map<Integer, MinesweeperType>> positions = new HashMap<>();
		for (int i = 0; i < bombs; i++) {
			Map<Integer, MinesweeperType> position;
			int x, y;
			do {
				x = event.getRandom().nextInt(gridX);
				y = event.getRandom().nextInt(gridY);

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
				for (int aroundX = x - 1; aroundX < x + 2; aroundX++) {
					for (int aroundY = y - 1; aroundY < y + 2; aroundY++) {
						MinesweeperType type = positions.getOrDefault(aroundX, new HashMap<>())
							.getOrDefault(aroundY, MinesweeperType.UNKNOWN);

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

			if (x != gridX - 1) {
				result.append("\n");
			}
		}

		event.reply(result.toString()).queue();
	}

}
