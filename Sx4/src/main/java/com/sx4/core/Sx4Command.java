package com.sx4.core;

import java.lang.reflect.Method;

import com.jockie.bot.core.command.impl.CommandImpl;
import com.sx4.interfaces.Donator;
import com.sx4.interfaces.Example;

public class Sx4Command extends CommandImpl {
	
	public boolean donator = false;
	public String example = null;
	
	public Sx4Command(String name) {
		super(name, true);
		
		this.doAnnotations();
	}
	
	public Sx4Command(String name, Method method, Object invoker) {
		super(name, method, invoker);
		
		this.doAnnotations();
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
