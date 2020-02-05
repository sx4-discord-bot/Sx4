package com.sx4.bot.core;

import com.jockie.bot.core.category.impl.CategoryImpl;

public class Sx4Category extends CategoryImpl {
	
	private final String[] aliases;

	public Sx4Category(String name, String description, String... aliases) {
		super(name, description, null);
		
		this.aliases = aliases;
	}
	
	public String[] getAliases() {
		return this.aliases;
	}
	
}
