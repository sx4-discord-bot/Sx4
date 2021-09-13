package com.sx4.bot.commands.games;

import com.jockie.bot.core.argument.Argument;
import com.sx4.bot.annotations.argument.DefaultNumber;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MinesweeperCommand extends Sx4Command {

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

		Map<Integer, Map<Integer, String>> positions = new HashMap<>();
		for (int i = 0; i < bombs; i++) {
			Map<Integer, String> position;
			int x, y;
			do {
				x = event.getRandom().nextInt(gridX);
				y = event.getRandom().nextInt(gridY);

				position = positions.computeIfAbsent(x, key -> new HashMap<>());
			} while (position.containsKey(y));

			position.put(y, "\uD83D\uDCA3");
		}

		List<Integer> zeros = new ArrayList<>();
		for (int x = 0; x < gridX; x++) {
			for (int y = 0; y < gridY; y++) {
				if (positions.getOrDefault(x, new HashMap<>()).containsKey(y)) {
					continue;
				}

				int amount = 0;
				for (int aroundX = x - 1; aroundX < x + 2; aroundX++) {
					for (int aroundY = y - 1; aroundY < y + 2; aroundY++) {
						String type = positions.getOrDefault(aroundX, new HashMap<>()).get(aroundY);
						if (type != null && type.equals("\uD83D\uDCA3")) {
							amount++;
						}
					}
				}

				if (amount == 0) {
					zeros.add(x * gridX + y);
				}

				positions.computeIfAbsent(x, key -> new HashMap<>()).put(y, Character.toString('\u0030' + amount) + "\u20E3");
			}
		}

		int exposedZero = zeros.size() == 0 ? -1 : zeros.get(event.getRandom().nextInt(zeros.size()));

		StringBuilder result = new StringBuilder();
		for (int x = 0; x < gridX; x++) {
			for (int y = 0; y < gridY; y++) {
				String current = positions.get(x).get(y);

				if (exposedZero == x * gridX + y) {
					result.append(current);
					continue;
				}

				result.append("||").append(current).append("||");
			}

			if (x != gridX - 1) {
				result.append("\n");
			}
		}

		event.reply(result.toString()).queue();
	}

}
