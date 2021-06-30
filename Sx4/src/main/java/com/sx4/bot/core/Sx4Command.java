package com.sx4.bot.core;

import com.jockie.bot.core.category.ICategory;
import com.jockie.bot.core.command.CommandTrigger;
import com.jockie.bot.core.command.ICommand;
import com.jockie.bot.core.command.impl.CommandImpl;
import com.jockie.bot.core.command.impl.DummyCommand;
import com.sx4.bot.annotations.command.*;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Message;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;

public class Sx4Command extends CommandImpl {

	protected int id;
	
	protected boolean premium = false;
	
	protected String[] examples = {};
	protected String[] redirects = {};
	
	protected boolean disabled = false;
	protected String disabledMessage = null;

	protected String cooldownMessage = null;
	
	protected boolean canaryCommand = false;
	
	public Sx4Command(String name, int id) {
		super(name, true);

		this.id = id;

		this.setBotDiscordPermissions(Permission.MESSAGE_WRITE);
	
		this.checkAnnotations();
	}
	
	public Sx4Command(String name, Method method, Object invoker) {
		super(name, method, invoker);

		this.setBotDiscordPermissions(Permission.MESSAGE_WRITE);

		this.checkIdAnnotation();
		this.checkAnnotations();
	}

	public int getId() {
		return this.id;
	}

	public Sx4Command setCooldownMessage(String message) {
		this.cooldownMessage = message;

		return this;
	}

	public String getCooldownMessage() {
		return this.cooldownMessage;
	}

	public boolean hasCooldownMessage() {
		return this.cooldownMessage != null;
	}
	
	public Sx4Command setCanaryCommand(boolean canaryCommand) {
		this.canaryCommand = canaryCommand;
		
		return this;
	}
	
	public boolean isCanaryCommand() {
		return this.canaryCommand;
	}
	
	public void enable() {
		this.disabled = false;
		this.disabledMessage = null;
	}
	
	public void disable() {
		this.disable(null);
	}
	
	public void disable(String message) {
		this.disabled = true;
		this.disabledMessage = message;
	}
	
	public boolean isDisabled() {
		return this.disabled;
	}
	
	public boolean hasDisabledMessage() {
		return this.disabledMessage != null;
	}
	
	public String getDisabledMessage() {
		return this.disabledMessage;
	}
	
	public String[] getRedirects() {
		return this.redirects;
	}
	
	public Sx4Command setRedirects(String... redirects) {
		this.redirects = redirects;
		
		return this;
	}
	
	public String[] getExamples() {
		return this.examples;
	}
	
	public Sx4Command setExamples(String... examples) {
		this.examples = examples;
		
		return this;
	}
	
	public boolean isPremiumCommand() {
		return this.premium;
	}
	
	public Sx4Command setPremiumCommand(boolean premium) {
		this.premium = premium;
		
		return this;
	}
	
	public Sx4Command setAuthorDiscordPermissions(Permission... permissions) {
		return this.setAuthorDiscordPermissions(false, permissions);
	}
	
	public Sx4Command setAuthorDiscordPermissions(boolean overwrite, Permission... permissions) {
		if (overwrite) {
			this.authorDiscordPermissions = permissions.length == 0 ? EnumSet.noneOf(Permission.class) : EnumSet.copyOf(Arrays.asList(permissions));
		} else {
			this.authorDiscordPermissions.addAll(Arrays.asList(permissions));
		}
		
		return this;
	}

	public EnumSet<Permission> getAuthorDiscordPermissions() {
		return this.authorDiscordPermissions;
	}
	
	public Sx4Command setBotDiscordPermissions(Permission... permissions) {
		return this.setBotDiscordPermissions(false, permissions);
	}
	
	public Sx4Command setBotDiscordPermissions(boolean overwrite, Permission... permissions) {
		if (overwrite) {
			this.botDiscordPermissions = permissions.length == 0 ? EnumSet.noneOf(Permission.class) : EnumSet.copyOf(Arrays.asList(permissions));
		} else {
			this.botDiscordPermissions.addAll(Arrays.asList(permissions));
		}
		
		return this;
	}
	
	public EnumSet<Permission> getBotDiscordPermissions() {
		return this.botDiscordPermissions;
	}
	
	public List<CommandTrigger> getAllCommandsRecursiveWithTriggers(Message message, String prefix) {
	    List<CommandTrigger> commands = super.getAllCommandsRecursiveWithTriggers(message, prefix);
	    
	    if (this.redirects.length != 0) {
	        List<ICommand> dummyCommands = commands.stream()
	            .map(CommandTrigger::getCommand)
	            .filter(command -> command instanceof DummyCommand)
	            .map(DummyCommand.class::cast)
	            .filter(command -> command.getActualCommand().equals(this))
	            .collect(Collectors.toList());
	        
	        for (String redirect : this.redirects) {
	            commands.add(new CommandTrigger(redirect, this));
	            
	            for (ICommand command : dummyCommands) {
	                commands.add(new CommandTrigger(redirect, command));
	            }
	        }
	    }
	    
	    return commands;
	}

	public Sx4Command setCategoryAll(ICategory category) {
		ICategory old = this.category;

		this.category = category;

		while (old != null) {
			this.getAllCommandsRecursive().forEach(old::removeCommand);

			old = old.getParent();
		}

		ICategory parent = this.category;
		while (parent != null) {
			this.getAllCommandsRecursive().forEach(parent::addCommand);

			parent = parent.getParent();
		}

		return this;
	}

	private void checkIdAnnotation() {
		CommandId id = this.method.getAnnotation(CommandId.class);
		if (id != null) {
			this.id = id.value();
		} else {
			throw new IllegalStateException(this.getCommandTrigger() + " does not have an id");
		}
	}
	
	private void checkAnnotations() {
		if (this.method != null) {
			Premium premium = this.method.getAnnotation(Premium.class);
			if (premium != null) {
				this.premium = premium.value();
			}

			Examples examples = this.method.getAnnotation(Examples.class);
			if (examples != null) {
				this.examples = examples.value();
			}

			Canary canary = this.method.getAnnotation(Canary.class);
			if (canary != null) {
				this.canaryCommand = canary.value();
			}

			Redirects redirects = this.method.getAnnotation(Redirects.class);
			if (redirects != null) {
				this.redirects = redirects.value();
			}

			CooldownMessage cooldownMessage = this.method.getAnnotation(CooldownMessage.class);
			if (cooldownMessage != null && !cooldownMessage.value().isEmpty()) {
				this.cooldownMessage = cooldownMessage.value();
			}
			
			AuthorPermissions authorPermissions = this.method.getAnnotation(AuthorPermissions.class);
			if (authorPermissions != null) {
				this.setAuthorDiscordPermissions(authorPermissions.overwrite(), authorPermissions.permissions());
			}
			
			BotPermissions botPermissions = this.method.getAnnotation(BotPermissions.class);
			if (botPermissions != null) {
				this.setBotDiscordPermissions(botPermissions.overwrite(), botPermissions.permissions());
			}
		}
	}
}
