package com.sx4.bot.formatter.output.function;

import java.util.function.Function;

public class FormatterParser<Type> {

	private final Class<Type> type;
	private final Function<String, Type> function;

	public FormatterParser(Class<Type> type, Function<String, Type> function) {
		this.type = type;
		this.function = function;
	}

	public Class<Type> getType() {
		return this.type;
	}

	public Function<String, Type> getFunction() {
		return this.function;
	}

	public Type parse(String text) {
		return this.function.apply(text);
	}

}
