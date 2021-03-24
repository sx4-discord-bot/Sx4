package com.sx4.bot.exceptions.mod;

public class AuthorHierarchyException extends HierarchyException {

	public AuthorHierarchyException(String type) {
		super(type, "You cannot " + type + " someone higher or equal than your top role");
	}
	
}
