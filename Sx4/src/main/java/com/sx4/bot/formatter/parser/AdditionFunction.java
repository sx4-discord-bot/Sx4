package com.sx4.bot.formatter.parser;

import com.sx4.bot.formatter.function.FormatterEvent;
import com.sx4.bot.formatter.function.FormatterFunction;

public class AdditionFunction extends FormatterFunction<Number> {

	public AdditionFunction() {
		super(Number.class, "add");
	}

	public Number parse(FormatterEvent event, Number number) {
		return ((Number) event.getObject()).doubleValue() + number.doubleValue();
	}

}
