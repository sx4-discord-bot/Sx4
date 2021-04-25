package com.sx4.bot.formatter.parser;

import com.sx4.bot.formatter.function.FormatterEvent;
import com.sx4.bot.formatter.function.FormatterFunction;

public class LessThanFunction extends FormatterFunction<Number> {

	public LessThanFunction() {
		super(Number.class, "lt");
	}

	public boolean parse(FormatterEvent event, Number number) {
		return ((Number) event.getObject()).doubleValue() < number.doubleValue();
	}

}