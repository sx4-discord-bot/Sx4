package com.sx4.bot.entities.games;

public enum GameState {

	WIN(0),
	DRAW(1),
	LOSS(2);

	private final int id;

	private GameState(int id) {
		this.id = id;
	}

	public int getId() {
		return this.id;
	}

	public static GameState fromId(int id) {
		for (GameState state : GameState.values()) {
			if (state.getId() == id) {
				return state;
			}
		}

		return null;
	}

}
