package com.sx4.bot.entities.info;

public enum ServerStatsType {

	MESSAGES("messages"),
	JOINS("joins");

	private final String field;

	private ServerStatsType(String field) {
		this.field = field;
	}

	public String getField() {
		return this.field;
	}

}
