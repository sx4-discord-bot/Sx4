package com.sx4.bot.entities.management.logger;

public enum LoggerCategory {

	USER(0),
	TEXT_CHANNEL(1),
	VOICE_CHANNEL(2),
	STORE_CHANNEL(3),
	CATEGORY(4),
	ROLE(5),
	AUDIT(6);

	private final int type;

	private LoggerCategory(int type) {
		this.type = type;
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
