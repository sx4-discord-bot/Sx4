package com.sx4.bot.formatter.function;

import com.sx4.bot.formatter.Formatter;

public class FormatterCondition {

	private final boolean condition;
	private final Object then;

	public FormatterCondition(boolean condition, Object object) {
		this.condition = condition;
		this.then = object;
	}

	public Object orElse(Object object) {
		return this.condition ? this.then : object;
	}

	@Override
	public String toString() {
		return Formatter.toString(this.orElse(""));
	}

}
