package com.sx4.bot.formatter.parser;

import com.sx4.bot.formatter.function.FormatterEvent;
import com.sx4.bot.formatter.function.FormatterFunction;

public class ModFunction extends FormatterFunction<Number> {

	public ModFunction() {
		super(Number.class, "mod");
	}

	public double parse(FormatterEvent<Number> event, Number number) {
		return event.getObject().doubleValue() % number.doubleValue();
	}

}
