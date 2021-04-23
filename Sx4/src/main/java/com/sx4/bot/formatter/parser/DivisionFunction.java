package com.sx4.bot.formatter.parser;

import com.sx4.bot.formatter.function.FormatterEvent;
import com.sx4.bot.formatter.function.FormatterFunction;

public class DivisionFunction extends FormatterFunction<Number> {

	public DivisionFunction() {
		super(Number.class, "divide");
	}

	public Number parse(FormatterEvent event, Number number) {
		return ((Number) event.getObject()).doubleValue() / number.doubleValue();
	}

}
