package com.sx4.bot.entities.management;

public enum WhitelistType {

	CHANNEL(0),
	CATEGORY(1);

	private final int id;

	private WhitelistType(int id) {
		this.id = id;
	}

	public int getId() {
		return this.id;
	}

	public static WhitelistType fromId(int id) {
		for (WhitelistType type : WhitelistType.values()) {
			if (type.getId() == id) {
				return type;
			}
		}

		return null;
	}

}
