package com.sx4.bot.entities.mod.auto;

public enum RegexType {

	REGEX(0),
	INVITE(1);

	private final int id;

	private RegexType(int id) {
		this.id = id;
	}

	public int getId() {
		return this.id;
	}

	public static RegexType fromId(int id) {
		for (RegexType type : RegexType.values()) {
			if (type.getId() == id) {
				return type;
			}
		}

		return null;
	}

}
