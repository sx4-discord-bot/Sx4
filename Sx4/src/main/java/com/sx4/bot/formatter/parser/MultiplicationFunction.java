package com.sx4.bot.formatter.parser;

import com.sx4.bot.formatter.function.FormatterEvent;
import com.sx4.bot.formatter.function.FormatterFunction;

public class MultiplicationFunction extends FormatterFunction<Number> {

	public MultiplicationFunction() {
		super(Number.class, "multiply");
	}

	public Number parse(FormatterEvent event, Number number) {
		return ((Number) event.getObject()).doubleValue() * number.doubleValue();
	}

}
