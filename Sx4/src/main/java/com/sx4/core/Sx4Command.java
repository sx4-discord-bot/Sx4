package com.sx4.core;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import com.jockie.bot.core.command.impl.CommandImpl;
import com.sx4.interfaces.Donator;
import com.sx4.interfaces.Example;
import com.sx4.translations.CommandTranslation;
import com.sx4.translations.TranslationType;

public class Sx4Command extends CommandImpl {
	
	protected boolean donator = false;
	
	protected String example = null;
	
	protected List<CommandTranslation> commandTranslations = null;
	
	protected boolean disabled = false;
	protected String disabledMessage = null;
	
	public Sx4Command(String name) {
		super(name, true);
		
		this.doAnnotations();
	}
	
	public Sx4Command(String name, Method method, Object invoker) {
		super(name, method, invoker);
		
		this.doAnnotations();
	}
	
	public CommandTranslation getCommandTranslation() {
		return this.getCommandTranslation(TranslationType.UK);
	}
	
	public CommandTranslation getCommandTranslation(TranslationType translationType) {
		for (CommandTranslation commandTranslation : this.commandTranslations) {
			if (commandTranslation.getTranslationType().equals(translationType)) {
				return commandTranslation;
			}
		}
		
		return null;
	}
	
	public String getDescription() {
		return this.getDescription(TranslationType.UK);
	}
	
	public String getDescription(TranslationType translationType) {
		CommandTranslation commandTranslation = this.getCommandTranslation(translationType);
		
		return commandTranslation == null ? null : commandTranslation.getDescription();
	}
	
	public Sx4Command setDescription(String description) {
		return this.setDescription(TranslationType.UK, description);
	}
	
	public Sx4Command setDescription(TranslationType translationType, String description) {
		for (CommandTranslation commandTranslation : this.commandTranslations) {
			if (commandTranslation.getTranslationType().equals(translationType)) {
				this.commandTranslations.remove(commandTranslation);
				commandTranslation.setDescription(description);
				this.commandTranslations.add(commandTranslation);
				
				return this;
			}
		}
		
		this.commandTranslations.add(new CommandTranslation(translationType, description, null));
		
		return this;
	}
	
	public Map<String, String> getStrings() {
		return this.getStrings(TranslationType.UK);
	}
	
	public Map<String, String> getStrings(TranslationType translationType) {
		CommandTranslation commandTranslation = this.getCommandTranslation(translationType);
		
		return commandTranslation == null ? null : commandTranslation.getStrings();
	}
	
	public Sx4Command setStrings(Map<String, String> strings) {
		return this.setStrings(TranslationType.UK, strings);
	}
	
	public Sx4Command setStrings(TranslationType translationType, Map<String, String> strings) {
		for (CommandTranslation commandTranslation : this.commandTranslations) {
			if (commandTranslation.getTranslationType().equals(translationType)) {
				this.commandTranslations.remove(commandTranslation);
				commandTranslation.setStrings(strings);
				this.commandTranslations.add(commandTranslation);
				
				return this;
			}
		}
		
		this.commandTranslations.add(new CommandTranslation(translationType, null, strings));
		
		return this;
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
	
	public String getExample() {
		return this.example;
	}
	
	public Sx4Command setExample(String example) {
		this.example = example;
		
		return this;
	}
	
	public boolean isDonator() {
		return this.donator;
	}
	
	public Sx4Command setDonator(boolean donator) {
		this.donator = donator;
		
		return this;
	}
	
	private void doAnnotations() {
		if (this.method != null) {
			if (this.method.isAnnotationPresent(Donator.class)) {
				this.donator = this.method.getAnnotation(Donator.class).value();
			} 
			
			if (this.method.isAnnotationPresent(Example.class)) {
				this.example = this.method.getAnnotation(Example.class).value();
			}
		}
	}
}
