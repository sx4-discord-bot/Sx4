package com.sx4.bot.entities.argument;

public class All<Type> {
	
	private final Type value;

	public All(Type value) {
		this.value = value;
	}
	
	public boolean isAll() {
		return this.value == null;
	}
	
	public Type getValue() {
		return this.value;
	}
	
}
