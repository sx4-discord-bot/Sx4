package com.sx4.bot.formatter.function;

import com.sx4.bot.formatter.FormatterManager;

public class FormatterEvent {

	private final Object object;
	private final FormatterManager manager;

	public FormatterEvent(Object object, FormatterManager manager) {
		this.object = object;
		this.manager = manager;
	}

	public Object getObject() {
		return this.object;
	}

	public void addVariable(String name, Object value) {
		this.manager.addVariable(name, Void.class, $ -> value);
	}

	public FormatterManager getManager() {
		return this.manager;
	}

}
