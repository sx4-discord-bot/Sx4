package com.sx4.bot.exceptions.mod;

public class BotHierarchyException extends HierarchyException {

	public BotHierarchyException(String type) {
		super(type, "I cannot " + type + " someone higher or equal than my top role");
	}
	
}
