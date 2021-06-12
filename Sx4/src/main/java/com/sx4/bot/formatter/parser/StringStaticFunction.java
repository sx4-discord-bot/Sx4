package com.sx4.bot.formatter.parser;

import com.sx4.bot.formatter.function.FormatterEvent;
import com.sx4.bot.formatter.function.FormatterStaticFunction;

public class StringStaticFunction extends FormatterStaticFunction {

	public StringStaticFunction() {
		super("string", "Allows you to define your own string without a variable");
	}

	public String parse(FormatterEvent<Void> event, String text) {
		return text;
	}

}
