package com.sx4.bot.entities.info;

import org.bson.Document;

import java.util.function.Function;

public enum FreeGameType {

	EPIC_GAMES(0, EpicFreeGame::fromDatabase),
	STEAM(1, SteamFreeGame::fromDatabase);

	private final int id;
	private final Function<Document, FreeGame<?>> function;

	private FreeGameType(int id, Function<Document, FreeGame<?>> function) {
		this.id = id;
		this.function = function;
	}

	public int getId() {
		return this.id;
	}

	public FreeGame<?> fromDatabase(Document data) {
		return this.function.apply(data);
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
