package com.sx4.bot.formatter.output.parser;

import com.sx4.bot.formatter.output.function.FormatterEvent;
import com.sx4.bot.formatter.output.function.FormatterStaticFunction;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.Temporal;

public class DateStaticFunction extends FormatterStaticFunction {

	public DateStaticFunction() {
		super("date", "Constructs a date object from year, month, day, hour, minute, second and offset");
	}

	public Temporal parse(FormatterEvent<Void> event, Integer year, Integer month, Integer day, Integer hour, Integer minute, Integer second, Integer offset) {
		return OffsetDateTime.of(year, month, day, hour, minute, second, 0, ZoneOffset.ofHours(offset));
	}

}
