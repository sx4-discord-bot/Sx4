package com.sx4.bot.formatter.output.function;

import java.lang.reflect.Type;
import java.util.function.Function;

public class FormatterVariable<T> {

	private final Class<T> type;
	private final Type returnType;
	private final String variable;
	private final Function<T, Object> function;

	private final String description;

	public FormatterVariable(String variable, String description, Class<T> type, Type returnType, Function<T, Object> function) {
		this.variable = variable;
		this.type = type;
		this.function = function;
		this.description = description;
		this.returnType = returnType;
	}

	public String getDescription() {
		return this.description;
	}

	public String getName() {
		return this.variable;
	}

	public Class<T> getType() {
		return this.type;
	}

	public Type getReturnType() {
		return this.returnType;
	}

	public Function<T, Object> getFunction() {
		return this.function;
	}

	@SuppressWarnings("unchecked")
	public Object parse(Object object) {
		return this.function.apply((T) object);
	}

}
