package com.sx4.bot.formatter.function;

import com.sx4.bot.formatter.FormatterManager;

public class FormatterEvent<Type> {

	private final Type object;
	private final FormatterManager manager;

	public FormatterEvent(Type  object, FormatterManager manager) {
		this.object = object;
		this.manager = manager;
	}

	public Type getObject() {
		return this.object;
	}

	public void addVariable(String name, Object value) {
		this.manager.addVariable(name, Void.class, $ -> value);
	}

	public FormatterManager getManager() {
		return this.manager;
	}

}
