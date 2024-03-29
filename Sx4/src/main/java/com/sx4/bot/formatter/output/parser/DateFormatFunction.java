package com.sx4.bot.formatter.output.parser;

import com.sx4.bot.formatter.output.function.FormatterEvent;
import com.sx4.bot.formatter.output.function.FormatterFunction;

import java.time.format.DateTimeFormatter;
import java.time.temporal.Temporal;

public class DateFormatFunction extends FormatterFunction<Temporal> {

	public DateFormatFunction() {
		super(Temporal.class, "format", "Formats a date to DateTimeFormatter standard");
	}

	public String parse(FormatterEvent<Temporal> event, String pattern) {
		Temporal time = event.getObject();
		try {
			return DateTimeFormatter.ofPattern(pattern).format(time);
		} catch (IllegalArgumentException e) {
			return DateTimeFormatter.ISO_DATE_TIME.format(time);
		}
	}

}
