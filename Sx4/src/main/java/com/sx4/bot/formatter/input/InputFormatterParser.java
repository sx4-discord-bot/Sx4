package com.sx4.bot.formatter.input;

import java.util.Map;

public class InputFormatterParser<Type> {

	private final String name;
	private final InputFormatterFunction<Type> function;

	public InputFormatterParser(String name, InputFormatterFunction<Type> function) {
		this.name = name;
		this.function = function;
	}

	public String getName() {
		return this.name;
	}

	public InputFormatterFunction<Type> getFunction() {
		return this.function;
	}

	public Type parse(InputFormatterContext context, String text, Map<String, Object> options) {
		return this.function.parse(context, text, options);
	}

}
