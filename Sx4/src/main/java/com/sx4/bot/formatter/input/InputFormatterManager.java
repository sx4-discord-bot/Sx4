package com.sx4.bot.formatter.input;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class InputFormatterManager {

	private static InputFormatterManager defaultManager = new InputFormatterManager();

	private final Map<String, InputFormatterParser<?>> parsers;
	private final Map<String, InputFormatterType> types;

	public InputFormatterManager() {
		this.parsers = new HashMap<>();
		this.types = new HashMap<>();
	}

	private InputFormatterManager(InputFormatterManager manager) {
		this.parsers = new HashMap<>(manager.getParsers());
		this.types = new HashMap<>(manager.getTypes());
	}

	public Function<String, ?> getMapping(String name, String key) {
		InputFormatterType type = this.types.get(name);
		if (type == null) {
			return Function.identity();
		}

		Function<String, ?> mapping = type.getMapping(key);
		return mapping == null ? Function.identity() : mapping;
	}

	public InputFormatterManager addMapping(String name, String key, Function<String, ?> mapping) {
		this.types.compute(name, (k, value) -> {
			InputFormatterType type = value == null ? new InputFormatterType(name, new HashMap<>()) : value;
			type.addMapping(key, mapping);
			return type;
		});

		return this;
	}

	public Map<String, InputFormatterType> getTypes() {
		return this.types;
	}

	public <Type> InputFormatterManager addParser(InputFormatterParser<Type> parser) {
		this.parsers.put(parser.getName(), parser);

		return this;
	}

	public <Type> InputFormatterManager addParser(String name, InputFormatterFunction<Type> function) {
		return this.addParser(new InputFormatterParser<>(name, function));
	}

	public InputFormatterParser<?> getParser(String name) {
		return this.parsers.get(name);
	}

	public Map<String, InputFormatterParser<?>> getParsers() {
		return this.parsers;
	}

	public static InputFormatterManager getDefaultManager() {
		return new InputFormatterManager(InputFormatterManager.defaultManager);
	}

	public static void setDefaultManager(InputFormatterManager manager) {
		InputFormatterManager.defaultManager = manager;
	}

}
