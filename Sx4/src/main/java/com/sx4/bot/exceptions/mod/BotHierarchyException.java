package com.sx4.bot.exceptions.mod;

public class BotHierarchyException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	private final String type;

	public BotHierarchyException(String type) {
		super("I cannot " + type + " a user higher or equal than my top role");
		this.type = type;
	}

	public String getType() {
		return this.type;
	}
	
}
