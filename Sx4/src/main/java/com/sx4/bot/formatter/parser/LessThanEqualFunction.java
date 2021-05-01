package com.sx4.bot.formatter.parser;

import com.sx4.bot.formatter.function.FormatterEvent;
import com.sx4.bot.formatter.function.FormatterFunction;

public class LessThanEqualFunction extends FormatterFunction<Number> {

	public LessThanEqualFunction() {
		super(Number.class, "lte");
	}

	public boolean parse(FormatterEvent<Number> event, Double number) {
		return event.getObject().doubleValue() <= number;
	}

}
