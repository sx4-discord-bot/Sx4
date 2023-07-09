package com.sx4.bot.core;

import com.jockie.bot.core.category.ICategory;
import com.jockie.bot.core.category.impl.CategoryImpl;
import com.jockie.bot.core.command.ICommand;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

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
	
	public @NotNull ICategory addCommand(ICommand command) {
		this.commands.add(command);
		
		return this;
	}
	
	public @NotNull ICategory removeCommand(ICommand command) {
		this.commands.remove(command);
		
		return this;
	}
	
	public @NotNull Set<ICommand> getCommands() {
		return this.getCommands(false);
	}
	
	public Set<ICommand> getCommands(boolean includeDeveloper) {
		return this.commands
			.stream()
			.filter(command -> !(!includeDeveloper && command.isDeveloperCommand()))
			.collect(Collectors.toUnmodifiableSet());
	}

	public int hashCode() {
		return this.getName().hashCode();
	}
	
}
