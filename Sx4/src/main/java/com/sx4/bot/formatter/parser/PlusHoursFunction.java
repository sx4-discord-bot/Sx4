package com.sx4.bot.formatter.parser;

import com.sx4.bot.formatter.function.FormatterEvent;
import com.sx4.bot.formatter.function.FormatterFunction;

import java.time.temporal.ChronoUnit;
import java.time.temporal.Temporal;

public class PlusHoursFunction extends FormatterFunction<Temporal> {

	public PlusHoursFunction() {
		super(Temporal.class, "plusHours");
	}

	public Temporal parse(FormatterEvent<Temporal> event, Integer amount) {
		return event.getObject().plus(amount, ChronoUnit.HOURS);
	}

}
