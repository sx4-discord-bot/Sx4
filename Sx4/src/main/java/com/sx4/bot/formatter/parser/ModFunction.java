package com.sx4.bot.formatter.parser;

import com.sx4.bot.formatter.function.FormatterEvent;
import com.sx4.bot.formatter.function.FormatterFunction;

public class ModFunction extends FormatterFunction<Number> {

	public ModFunction() {
		super(Number.class, "mod", "Finds the remainder when dividing a number by the other");
	}

	public double parse(FormatterEvent<Number> event, Number number) {
		return event.getObject().doubleValue() % number.doubleValue();
	}

}
