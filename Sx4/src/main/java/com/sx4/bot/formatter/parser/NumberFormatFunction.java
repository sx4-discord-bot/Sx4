package com.sx4.bot.formatter.parser;

import com.sx4.bot.formatter.function.FormatterEvent;
import com.sx4.bot.formatter.function.FormatterFunction;

import java.text.DecimalFormat;

public class NumberFormatFunction extends FormatterFunction<Number> {

	public NumberFormatFunction() {
		super(Number.class, "format");
	}

	public String parse(FormatterEvent event, String format) {
		try {
			return new DecimalFormat(format).format(((Number) event.getObject()).doubleValue());
		} catch (IllegalArgumentException e) {
			return event.getObject().toString();
		}
	}

}
