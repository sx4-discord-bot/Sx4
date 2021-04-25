package com.sx4.bot.formatter.parser;

import com.sx4.bot.formatter.function.FormatterEvent;
import com.sx4.bot.formatter.function.FormatterFunction;

public class GreaterThanEqualFunction extends FormatterFunction<Number> {

	public GreaterThanEqualFunction() {
		super(Number.class, "gte");
	}

	public boolean parse(FormatterEvent event, Double number) {
		return ((Number) event.getObject()).doubleValue() >= number;
	}

}
