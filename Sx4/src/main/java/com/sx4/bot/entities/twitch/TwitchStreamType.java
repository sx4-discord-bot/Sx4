package com.sx4.bot.entities.twitch;

public enum TwitchStreamType {

	STREAM("live"),
	PLAYLIST("playlist"),
	WATCH_PARTY("watch_party"),
	PREMIERE("premiere"),
	RERUN("rerun");

	private final String identifier;

	private TwitchStreamType(String identifier) {
		this.identifier = identifier;
	}

	public String getIdentifier() {
		return this.identifier;
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
