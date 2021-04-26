package com.sx4.bot.formatter.parser;

import com.sx4.bot.formatter.function.FormatterEvent;
import com.sx4.bot.formatter.function.FormatterFunction;

public class StringStaticFunction extends FormatterFunction<Void> {

	public StringStaticFunction() {
		super(Void.class, "string");
	}

	public String parse(FormatterEvent event, String text) {
		return text;
	}

}
