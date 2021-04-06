package com.sx4.bot.entities.management;

public enum LoggerCategory {

	USER("User", 0),
	TEXT_CHANNEL("Text Channel", 1),
	VOICE_CHANNEL("Voice Channel", 2),
	STORE_CHANNEL("Store Channel", 3),
	CATEGORY("Category", 4),
	ROLE("Role", 5),
	AUDIT("User", 6),
	EMOTE("Emote", 7);

	private final int type;
	private final String name;

	private LoggerCategory(String name, int type) {
		this.name = name;
		this.type = type;
	}

	public String getName() {
		return this.name;
	}

	public int getType() {
		return this.type;
	}

	public static LoggerCategory fromType(int type) {
		for (LoggerCategory category : LoggerCategory.values()) {
			if (category.getType() == type) {
				return category;
			}
		}

		return null;
	}

}
