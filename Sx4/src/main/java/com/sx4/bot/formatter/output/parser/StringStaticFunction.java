package com.sx4.bot.formatter.output.parser;

import com.sx4.bot.formatter.output.function.FormatterEvent;
import com.sx4.bot.formatter.output.function.FormatterStaticFunction;

public class StringStaticFunction extends FormatterStaticFunction {

	public StringStaticFunction() {
		super("string", "Allows you to define your own string without a variable");
	}

	public String parse(FormatterEvent<Void> event, String text) {
		return text;
	}

}
