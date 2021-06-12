package com.sx4.bot.formatter.parser;

import com.sx4.bot.formatter.IFormatter;
import com.sx4.bot.formatter.function.FormatterEvent;
import com.sx4.bot.formatter.function.FormatterFunction;

import java.time.format.DateTimeFormatter;
import java.time.temporal.Temporal;

public class DateFormatFunction extends FormatterFunction<Temporal> {

	public DateFormatFunction() {
		super(Temporal.class, "format", "Formats a date to DateTimeFormatter standard");
	}

	public String parse(FormatterEvent<Temporal> event, String pattern) {
		Temporal time = event.getObject();
		try {
			return DateTimeFormatter.ofPattern(IFormatter.format(pattern, event.getManager())).format(time);
		} catch (IllegalArgumentException e) {
			return DateTimeFormatter.ISO_DATE_TIME.format(time);
		}
	}

}
