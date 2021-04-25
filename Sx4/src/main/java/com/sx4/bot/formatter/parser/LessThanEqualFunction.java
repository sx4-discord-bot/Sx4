package com.sx4.bot.formatter.parser;

import com.sx4.bot.formatter.function.FormatterEvent;
import com.sx4.bot.formatter.function.FormatterFunction;

public class LessThanEqualFunction extends FormatterFunction<Number> {

	public LessThanEqualFunction() {
		super(Number.class, "lte");
	}

	public boolean parse(FormatterEvent event, Double number) {
		return ((Number) event.getObject()).doubleValue() <= number;
	}

}
