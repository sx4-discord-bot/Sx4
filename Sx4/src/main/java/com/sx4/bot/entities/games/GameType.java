package com.sx4.bot.entities.games;

public enum GameType {

	ROCK_PAPER_SCISSORS(0, "Rock Paper Scissors"),
	GUESS_THE_NUMBER(1, "Guess the Number"),
	RUSSIAN_ROULETTE(2, "Russian Roulette"),
	MYSTERY_BOX(3, "Mystery Box"),
	SLOT(4, "Slot");

	private final int id;
	private final String name;

	private GameType(int id, String name) {
		this.id = id;
		this.name = name;
	}

	public String getName() {
		return this.name;
	}

	public int getId() {
		return this.id;
	}

	public static GameType fromId(int id) {
		for (GameType type : GameType.values()) {
			if (type.getId() == id) {
				return type;
			}
		}

		return null;
	}

}
