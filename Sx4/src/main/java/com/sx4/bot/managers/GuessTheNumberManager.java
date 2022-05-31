package com.sx4.bot.managers;

import com.sx4.bot.entities.games.GuessTheNumberGame;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class GuessTheNumberManager {

	private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

	private final Map<Long, GuessTheNumberGame> games;
	private final Map<Long, ScheduledFuture<?>> executors;

	public GuessTheNumberManager() {
		this.games = new HashMap<>();
		this.executors = new HashMap<>();
	}

	public boolean hasGame(long firstUserId, long secondUserId) {
		return this.games.values().stream().anyMatch(game -> (game.getOpponentId() == firstUserId && game.getUserId() == secondUserId) || (game.getOpponentId() == secondUserId && game.getUserId() == firstUserId));
	}

	public GuessTheNumberGame getGame(long messageId) {
		return this.games.get(messageId);
	}

	public void addGame(GuessTheNumberGame game) {
		this.games.put(game.getMessageId(), game);
		this.setTimeout(game);
	}

	public void removeGame(GuessTheNumberGame game) {
		this.games.remove(game.getMessageId());
		this.cancelTimeout(game);
	}

	public void cancelTimeout(GuessTheNumberGame game) {
		ScheduledFuture<?> future = this.executors.remove(game.getMessageId());
		if (future != null && !future.isDone()) {
			future.cancel(true);
		}
	}

	public void setTimeout(GuessTheNumberGame game) {
		this.executors.put(game.getMessageId(), this.executor.schedule(() -> this.games.remove(game.getMessageId()), 60, TimeUnit.SECONDS));
	}

}
