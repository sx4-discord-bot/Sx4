package com.sx4.bot.formatter.parser;

import com.sx4.bot.formatter.function.FormatterEvent;
import com.sx4.bot.formatter.function.FormatterFunction;

public class SubtractFunction extends FormatterFunction<Number> {

	public SubtractFunction() {
		super(Number.class, "subtract");
	}

	public Number parse(FormatterEvent event, Number number) {
		return ((Number) event.getObject()).doubleValue() - number.doubleValue();
	}

}
