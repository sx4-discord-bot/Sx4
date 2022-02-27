package com.sx4.bot.entities.info;

import org.bson.Document;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.function.Function;

public enum FreeGameType {

	EPIC_GAMES(0, EpicFreeGame::fromDatabase),
	STEAM(1, SteamFreeGame::fromDatabase);

	public static final long ALL = FreeGameType.getRaw(FreeGameType.values());

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

	public EnumSet<FreeGameType> fromRaw(long raw) {
		EnumSet<FreeGameType> types = EnumSet.noneOf(FreeGameType.class);
		if (raw == 0L) {
			return types;
		}

		for (FreeGameType type : FreeGameType.values()) {
			if ((raw & type.getId()) == type.getId()) {
				types.add(type);
			}
		}

		return types;
	}

	public static long getRaw(FreeGameType... types) {
		return FreeGameType.getRaw(Arrays.asList(types));
	}

	public static long getRaw(Collection<FreeGameType> types) {
		long raw = 0L;
		for (FreeGameType type : types) {
			raw |= type.getId();
		}

		return raw;
	}

}
