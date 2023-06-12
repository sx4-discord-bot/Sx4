package com.sx4.bot.formatter.output.parser;

import com.sx4.bot.formatter.output.function.FormatterEvent;
import com.sx4.bot.formatter.output.function.FormatterFunction;

public class SubtractFunction extends FormatterFunction<Number> {

	public SubtractFunction() {
		super(Number.class, "subtract", "Subtracts one number from another");
	}

	public Number parse(FormatterEvent<Number> event, Number number) {
		return event.getObject().doubleValue() - number.doubleValue();
	}

}
