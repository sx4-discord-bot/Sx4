package com.sx4.core;

import java.lang.reflect.Method;

import com.jockie.bot.core.command.impl.CommandImpl;
import com.sx4.interfaces.Donator;

public class Sx4Command extends CommandImpl {

	private boolean donator;
	
	public Sx4Command(String name) {
		super(name, true);
	}
	
	public Sx4Command(String name, Method method, Object invoker) {
		super(name, method, invoker);
	}
	
	public boolean isDonator() {
		return this.donator;
	}
	
	public Sx4Command setDonator(boolean donator) {
		this.donator = donator;
		
		return this;
	}
	
	protected void applyAnnotations() {
		super.applyAnnotations();
		
		if (this.method.isAnnotationPresent(Donator.class)) {
			this.setDonator(this.method.getAnnotation(Donator.class).value());
		}
	}
	
}
