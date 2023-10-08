package com.sx4.bot.formatter.output.parser;

import com.sx4.bot.formatter.output.function.FormatterEvent;
import com.sx4.bot.formatter.output.function.FormatterFunction;

public class ReplaceStringFunction extends FormatterFunction<String> {

	public ReplaceStringFunction() {
		super(String.class, "replace", "Replaces every instance of a string with another inside a string");
	}

	public String parse(FormatterEvent<String> event, String replace, String with) {
		return event.getObject().replace(replace, with);
	}

}
