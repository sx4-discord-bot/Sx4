package com.sx4.bot.entities.management;

import org.bson.Document;

public enum AutoRoleFilter {

	BOT(0, true, "Makes it so only bot accounts receive this auto role"),
	NOT_BOT(0, false, "Makes it so only non-bot accounts receive this auto role");
	/*CREATED_LESS_THAN("CREATED", false, "Makes it so users created less than the specified time receive this auto role", true),
	CREATED_MORE_THAN("CREATED", true, "Makes it so users created more than the specified time receive this auto role", true),
	JOINED_MORE_THAN("JOINED", true, "Makes it so users who have joined the server for more then the specified time receive this auto role", true),
	JOINED_LESS_THAN("JOINED", false, "Makes it so users who have joined the server for less then the specified time receive this auto role", true);*/
	
	private final int type;
	private final Object value;
	
	private final String description;
	private final boolean duration;
	
	private AutoRoleFilter(int type, Object value, String description) {
		this(type, value, description, false);
	}
	
	private AutoRoleFilter(int type, Object value, String description, boolean duration) {
		this.type = type;
		this.value = value;
		this.description = description;
		this.duration = duration;
	}
	
	public boolean hasDuration() {
		return this.duration;
	}
	
	public String getDescription() {
		return this.description;
	}
	
	public int getType() {
		return this.type;
	}
	
	public Object getValue() {
		return this.value;
	}
	
	public Document asDocument() {
		return new Document("type", this.type).append("value", this.value);
	}
	
}
