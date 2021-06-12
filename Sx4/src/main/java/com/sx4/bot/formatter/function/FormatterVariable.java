package com.sx4.bot.formatter.function;

import java.util.function.Function;

public class FormatterVariable<Type> {

	private final Class<Type> type;
	private final String variable;
	private final Function<Type, Object> function;

	private final String description;

	public FormatterVariable(String variable, String description, Class<Type> type, Function<Type, Object> function) {
		this.variable = variable;
		this.type = type;
		this.function = function;
		this.description = description;
	}

	public String getDescription() {
		return this.description;
	}

	public String getName() {
		return this.variable;
	}

	public Class<Type> getType() {
		return this.type;
	}

	public Function<Type, Object> getFunction() {
		return this.function;
	}

	@SuppressWarnings("unchecked")
	public Object parse(Object object) {
		return this.function.apply((Type) object);
	}

}
