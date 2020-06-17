package com.sx4.bot.entities.management;

import org.bson.Document;

public enum Filter {

	BOT("bot", false, "Makes it so only bot accounts receive this auto role"),
	NOT_BOT("bot", true, "Makes it so only non-bot accounts receive this auto role"),
	CREATED_LESS_THAN("created", false, "Makes it so users created less than the specified time receive this auto role", true),
	CREATED_MORE_THAN("created", true, "Makes it so users created more than the specified time receive this auto role", true),
	JOINED_MORE_THAN("joined", true, "Makes it so users who have joined the server for more then the specified time receive this auto role", true),
	JOINED_LESS_THAN("joined", false, "Makes it so users who have joined the server for less then the specified time receive this auto role", true);
	
	private final String key;
	private final Object value;
	
	private final String description;
	private final boolean hasDuration;
	
	private Filter(String key, Object value, String description) {
		this(key, value, description, false);
	}
	
	private Filter(String key, Object value, String description, boolean hasDuration) {
		this.key = key;
		this.value = value;
		this.description = description;
		this.hasDuration = hasDuration;
	}
	
	public boolean hasDuration() {
		return this.hasDuration;
	}
	
	public String getDescription() {
		return this.description;
	}
	
	public String getKey() {
		return this.key;
	}
	
	public Object getValue() {
		return this.value;
	}
	
	public Document asDocument() {
		return new Document(key, value);
	}
	
}
