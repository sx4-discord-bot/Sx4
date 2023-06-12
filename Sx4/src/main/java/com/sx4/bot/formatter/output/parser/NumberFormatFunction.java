package com.sx4.bot.formatter.output.parser;

import com.sx4.bot.formatter.output.function.FormatterEvent;
import com.sx4.bot.formatter.output.function.FormatterFunction;

import java.text.DecimalFormat;

public class NumberFormatFunction extends FormatterFunction<Number> {

	public NumberFormatFunction() {
		super(Number.class, "format", "Formats a number by the standard of DecimalFormat");
	}

	public String parse(FormatterEvent<Number> event, String format) {
		try {
			return new DecimalFormat(format).format(event.getObject().doubleValue());
		} catch (IllegalArgumentException e) {
			return event.getObject().toString();
		}
	}

}
