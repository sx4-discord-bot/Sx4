package com.sx4.bot.entities.twitch;

public enum TwitchSubscriptionType {

	ONLINE(0, "stream.online");

	private final int id;
	private final String identifier;

	private TwitchSubscriptionType(int id, String identifier) {
		this.id = id;
		this.identifier = identifier;
	}

	public int getId() {
		return this.id;
	}

	public String getIdentifier() {
		return this.identifier;
	}

	public static TwitchSubscriptionType fromIdentifier(String identifier) {
		for (TwitchSubscriptionType type : TwitchSubscriptionType.values()) {
			if (type.getIdentifier().equals(identifier)) {
				return type;
			}
		}

		return null;
	}

}
