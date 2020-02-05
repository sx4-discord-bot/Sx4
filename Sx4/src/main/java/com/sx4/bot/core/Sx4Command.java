package com.sx4.bot.core;

import java.lang.reflect.Method;

import com.jockie.bot.core.command.impl.CommandImpl;
import com.sx4.bot.annotations.Canary;
import com.sx4.bot.annotations.Donator;
import com.sx4.bot.annotations.Examples;
import com.sx4.bot.hooks.mod.ModActionManager;

public class Sx4Command extends CommandImpl {
	
	public final ModActionManager modManager = Sx4Bot.getModActionManager();
	
	protected boolean donator = false;
	
	protected String[] examples = {};
	
	protected boolean disabled = false;
	protected String disabledMessage = null;
	
	protected boolean canaryCommand = false;
	
	public Sx4Command(String name) {
		super(name, true);
		
		this.doAnnotations();
	}
	
	public Sx4Command(String name, Method method, Object invoker) {
		super(name, method, invoker);
		
		this.doAnnotations();
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
	
	public String[] getExamples() {
		return this.examples;
	}
	
	public Sx4Command setExamples(String... examples) {
		this.examples = examples;
		
		return this;
	}
	
	public boolean isDonatorCommand() {
		return this.donator;
	}
	
	public Sx4Command setDonatorCommand(boolean donator) {
		this.donator = donator;
		
		return this;
	}
	
	private void doAnnotations() {
		if (this.method != null) {
			if (this.method.isAnnotationPresent(Donator.class)) {
				this.donator = this.method.getAnnotation(Donator.class).value();
			} 
			
			if (this.method.isAnnotationPresent(Examples.class)) {
				this.examples = this.method.getAnnotation(Examples.class).value();
			}
			
			if (this.method.isAnnotationPresent(Canary.class)) {
				this.canaryCommand = this.method.getAnnotation(Canary.class).value();
			}
		}
	}
}
