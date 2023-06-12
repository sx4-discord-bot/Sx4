package com.sx4.bot.formatter.output.parser;

import com.sx4.bot.formatter.output.function.FormatterEvent;
import com.sx4.bot.formatter.output.function.FormatterFunction;

public class LessThanEqualFunction extends FormatterFunction<Number> {

	public LessThanEqualFunction() {
		super(Number.class, "lte", "Checks if a number is less than or equal to another");
	}

	public boolean parse(FormatterEvent<Number> event, Double number) {
		return event.getObject().doubleValue() <= number;
	}

}
