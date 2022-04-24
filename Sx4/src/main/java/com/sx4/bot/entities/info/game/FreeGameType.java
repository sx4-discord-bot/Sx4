package com.sx4.bot.entities.info.game;

import org.bson.Document;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.function.Function;

public enum FreeGameType {

	EPIC_GAMES(0, "Epic Games", "https://cdn.discordapp.com/attachments/344091594972069888/950459635800887296/epic_games.png", EpicFreeGame::fromDatabase),
	STEAM(1, "Steam", "https://cdn.discordapp.com/attachments/344091594972069888/950459636157395024/steam.png", SteamFreeGame::fromDatabase),
	GOG(2, "GOG", "https://cdn.discordapp.com/attachments/344091594972069888/964575552671473664/gog.png", GOGFreeGame::fromDatabase);

	public static final long ALL = FreeGameType.getRaw(FreeGameType.values());

	private final int id;
	private final long raw;
	private final String name, iconUrl;
	private final Function<Document, FreeGame<?>> function;

	private FreeGameType(int id, String name, String iconUrl, Function<Document, FreeGame<?>> function) {
		this.id = id;
		this.raw = 1L << id;
		this.name = name;
		this.iconUrl = iconUrl;
		this.function = function;
	}

	public String getName() {
		return this.name;
	}

	public String getIconUrl() {
		return this.iconUrl;
	}

	public int getId() {
		return this.id;
	}

	public long getRaw() {
		return this.raw;
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
			if ((raw & type.getRaw()) == type.getRaw()) {
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
			raw |= type.getRaw();
		}

		return raw;
	}

}
