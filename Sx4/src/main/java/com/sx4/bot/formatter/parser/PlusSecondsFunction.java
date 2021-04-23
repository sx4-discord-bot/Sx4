package com.sx4.bot.formatter.parser;

import com.sx4.bot.formatter.function.FormatterEvent;
import com.sx4.bot.formatter.function.FormatterFunction;

import java.time.temporal.ChronoUnit;
import java.time.temporal.Temporal;

public class PlusSecondsFunction extends FormatterFunction<Temporal> {

	public PlusSecondsFunction() {
		super(Temporal.class, "plusSeconds");
	}

	public Temporal parse(FormatterEvent event, Long amount) {
		return ((Temporal) event.getObject()).plus(amount, ChronoUnit.SECONDS);
	}

}
