package com.sx4.bot.formatter.parser;

import com.sx4.bot.formatter.function.FormatterEvent;
import com.sx4.bot.formatter.function.FormatterFunction;

public class LessThanFunction extends FormatterFunction<Number> {

	public LessThanFunction() {
		super(Number.class, "lt", "Checks if another number is less than another");
	}

	public boolean parse(FormatterEvent<Number> event, Double number) {
		return event.getObject().doubleValue() < number;
	}

}
