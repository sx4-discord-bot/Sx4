package com.sx4.bot.core;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import com.jockie.bot.core.category.ICategory;
import com.jockie.bot.core.category.impl.CategoryImpl;
import com.jockie.bot.core.command.ICommand;

public class Sx4Category extends CategoryImpl {
	
	private final String[] aliases;
	private final Set<ICommand> commands;
	
	public Sx4Category(String name, String description, String... aliases) {
		this(name, description, null, aliases);
	}
	
	public Sx4Category(String name, String description, ICategory parent, String... aliases) {
		super(name, description, parent);
		
		this.aliases = aliases;
		this.commands = new HashSet<>();
	}
	
	public String[] getAliases() {
		return this.aliases;
	}
	
	public ICategory addCommand(ICommand command) {
		this.commands.add(command);
		
		return this;
	}
	
	public ICategory removeCommand(ICommand command) {
		this.commands.remove(command);
		
		return this;
	}
	
	public Set<ICommand> getCommands() {
		return Collections.unmodifiableSet(this.commands);
	}
	
}
