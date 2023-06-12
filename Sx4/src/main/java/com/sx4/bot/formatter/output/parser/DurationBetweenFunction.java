package com.sx4.bot.formatter.output.parser;

import com.sx4.bot.formatter.output.function.FormatterEvent;
import com.sx4.bot.formatter.output.function.FormatterFunction;

import java.time.temporal.ChronoUnit;
import java.time.temporal.Temporal;

public class DurationBetweenFunction extends FormatterFunction<Temporal> {

	public DurationBetweenFunction() {
		super(Temporal.class, "difference", "Find the difference between 2 dates and returns it in seconds");
	}

	public long parse(FormatterEvent<Temporal> event, Temporal time) {
		return event.getObject().until(time, ChronoUnit.SECONDS);
	}

}
