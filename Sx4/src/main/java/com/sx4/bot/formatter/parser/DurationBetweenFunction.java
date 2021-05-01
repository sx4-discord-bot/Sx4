package com.sx4.bot.formatter.parser;

import com.sx4.bot.formatter.function.FormatterEvent;
import com.sx4.bot.formatter.function.FormatterFunction;

import java.time.temporal.ChronoUnit;
import java.time.temporal.Temporal;

public class DurationBetweenFunction extends FormatterFunction<Temporal> {

	public DurationBetweenFunction() {
		super(Temporal.class, "difference");
	}

	public long parse(FormatterEvent<Temporal> event, Temporal time) {
		return event.getObject().until(time, ChronoUnit.SECONDS);
	}

}
