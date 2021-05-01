package com.sx4.bot.formatter.parser;

import com.sx4.bot.formatter.function.FormatterEvent;
import com.sx4.bot.formatter.function.FormatterFunction;

import java.time.temporal.ChronoUnit;
import java.time.temporal.Temporal;

public class PlusDaysFunction extends FormatterFunction<Temporal> {

	public PlusDaysFunction() {
		super(Temporal.class, "plusDays");
	}

	public Temporal parse(FormatterEvent<Temporal> event, Integer amount) {
		return event.getObject().plus(amount, ChronoUnit.DAYS);
	}

}
