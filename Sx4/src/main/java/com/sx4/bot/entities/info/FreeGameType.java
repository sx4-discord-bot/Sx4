package com.sx4.bot.entities.info;

public enum FreeGameType {

	EPIC_GAMES(0),
	STEAM(1);

	private final int id;

	private FreeGameType(int id) {
		this.id = id;
	}

	public int getId() {
		return this.id;
	}

	public static FreeGameType fromId(int id) {
		for (FreeGameType type : FreeGameType.values()) {
			if (type.getId() == id) {
				return type;
			}
		}

		return null;
	}

}
