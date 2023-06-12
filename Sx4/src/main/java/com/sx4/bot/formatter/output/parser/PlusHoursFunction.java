package com.sx4.bot.formatter.output.parser;

import com.sx4.bot.formatter.output.function.FormatterEvent;
import com.sx4.bot.formatter.output.function.FormatterFunction;

import java.time.temporal.ChronoUnit;
import java.time.temporal.Temporal;

public class PlusHoursFunction extends FormatterFunction<Temporal> {

	public PlusHoursFunction() {
		super(Temporal.class, "plusHours", "Adds a certain amount of hours to a date");
	}

	public Temporal parse(FormatterEvent<Temporal> event, Integer amount) {
		return event.getObject().plus(amount, ChronoUnit.HOURS);
	}

}
