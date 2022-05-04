package com.sx4.bot.entities.twitch;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;

public enum TwitchStreamType {

	LIVE(0, "live"),
	PLAYLIST(1, "playlist"),
	WATCH_PARTY(2, "watch_party"),
	PREMIERE(3, "premiere"),
	RERUN(4, "rerun");

	public static final long ALL = TwitchStreamType.getRaw(TwitchStreamType.values());

	private final long raw;
	private final String identifier;

	private TwitchStreamType(int id, String identifier) {
		this.raw = 1L << id;
		this.identifier = identifier;
	}

	public long getRaw() {
		return this.raw;
	}

	public String getIdentifier() {
		return this.identifier;
	}

	public static EnumSet<TwitchStreamType> getTypes(long raw) {
		EnumSet<TwitchStreamType> types = EnumSet.noneOf(TwitchStreamType.class);
		if (raw == 0) {
			return types;
		}

		for (TwitchStreamType type : TwitchStreamType.values()) {
			if ((raw & type.getRaw()) == type.getRaw()) {
				types.add(type);
			}
		}

		return types;
	}

	public static long getRaw(Collection<TwitchStreamType> types) {
		long raw = 0;
		for (TwitchStreamType type : types) {
			raw |= type.getRaw();
		}

		return raw;
	}

	public static long getRaw(TwitchStreamType... types) {
		return TwitchStreamType.getRaw(Arrays.asList(types));
	}

	public static TwitchStreamType fromIdentifier(String identifier) {
		for (TwitchStreamType type : TwitchStreamType.values()) {
			if (type.getIdentifier().equals(identifier)) {
				return type;
			}
		}

		return null;
	}

}
