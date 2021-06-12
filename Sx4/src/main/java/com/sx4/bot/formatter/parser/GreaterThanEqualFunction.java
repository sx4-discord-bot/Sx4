package com.sx4.bot.formatter.parser;

import com.sx4.bot.formatter.function.FormatterEvent;
import com.sx4.bot.formatter.function.FormatterFunction;

public class GreaterThanEqualFunction extends FormatterFunction<Number> {

	public GreaterThanEqualFunction() {
		super(Number.class, "gte", "Checks if a number is greater than or equal to another");
	}

	public boolean parse(FormatterEvent<Number> event, Double number) {
		return event.getObject().doubleValue() >= number;
	}

}
