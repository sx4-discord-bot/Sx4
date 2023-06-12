package com.sx4.bot.formatter.input;

import java.util.Map;
import java.util.function.BiFunction;

public class InputFormatterParser<Type> {

	private final String name;
	private final BiFunction<String, Map<String, Object>, Type> function;

	public InputFormatterParser(String name, BiFunction<String, Map<String, Object>, Type> function) {
		this.name = name;
		this.function = function;
	}

	public String getName() {
		return this.name;
	}

	public BiFunction<String, Map<String, Object>, Type> getFunction() {
		return this.function;
	}

	public Type parse(String text, Map<String, Object> options) {
		return this.function.apply(text, options);
	}

}
