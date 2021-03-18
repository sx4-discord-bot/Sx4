package com.sx4.bot.entities.games;

public enum GameType {

	RPS(0),
	GTN(1);

	private final int id;

	private GameType(int id) {
		this.id = id;
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
