package com.sx4.bot.formatter.input;

import java.util.Map;

public class InputFormatterArgument {

	private final String name;
	private final Map<String, Object> options;

	public InputFormatterArgument(String name, Map<String, Object> options) {
		this.name = name;
		this.options = options;
	}

	public String getName() {
		return this.name;
	}

	public Map<String, Object> getOptions() {
		return this.options;
	}

}
