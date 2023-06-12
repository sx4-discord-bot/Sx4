package com.sx4.bot.formatter.output.parser;

import com.sx4.bot.formatter.output.function.FormatterEvent;
import com.sx4.bot.formatter.output.function.FormatterFunction;

public class AdditionFunction extends FormatterFunction<Number> {

	public AdditionFunction() {
		super(Number.class, "add", "Adds two numbers together");
	}

	public Number parse(FormatterEvent<Number> event, Number number) {
		return event.getObject().doubleValue() + number.doubleValue();
	}

}
