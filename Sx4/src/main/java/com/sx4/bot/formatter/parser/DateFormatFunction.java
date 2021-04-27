package com.sx4.bot.formatter.parser;

import com.sx4.bot.formatter.IFormatter;
import com.sx4.bot.formatter.function.FormatterEvent;
import com.sx4.bot.formatter.function.FormatterFunction;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

public class DateFormatFunction extends FormatterFunction<OffsetDateTime> {

	public DateFormatFunction() {
		super(OffsetDateTime.class, "format");
	}

	public String parse(FormatterEvent event, String pattern) {
		OffsetDateTime time = (OffsetDateTime) event.getObject();
		try {
			return time.format(DateTimeFormatter.ofPattern(IFormatter.format(pattern, event.getManager())));
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
			return time.format(DateTimeFormatter.ISO_DATE_TIME);
		}
	}

}
