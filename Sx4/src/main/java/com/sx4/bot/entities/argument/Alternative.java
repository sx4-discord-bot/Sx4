package com.sx4.bot.entities.argument;

public class Alternative<Type> {
	
	private final Type value;
	private final String alternative;

	public Alternative(Type value, String alternative) {
		this.value = value;
		this.alternative = alternative;
	}
	
	public boolean isAlternative() {
		return this.value == null;
	}
	
	public Type getValue() {
		return this.value;
	}

	public String getAlternative() {
		return this.alternative;
	}
	
}
