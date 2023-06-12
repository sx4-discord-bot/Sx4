package com.sx4.bot.formatter.input;

import java.util.Map;
import java.util.function.Function;

public class InputFormatterType {

	private final String name;
	private final Map<String, Function<String, ?>> mappings;

	public InputFormatterType(String name, Map<String, Function<String, ?>> mappings) {
		this.name = name;
		this.mappings = mappings;
	}

	public String getName() {
		return this.name;
	}

	public Object getValue(String key, String value) {
		Function<String, ?> mapping = this.mappings.get(key);
		if (mapping == null) {
			return value;
		}

		return mapping.apply(value);
	}

	public Function<String, ?> getMapping(String key) {
		return this.mappings.get(key);
	}

	public InputFormatterType addMapping(String key, Function<String, ?> mapping) {
		this.mappings.put(key, mapping);

		return this;
	}

	public Map<String, Function<String, ?>> getMappings() {
		return this.mappings;
	}
}
