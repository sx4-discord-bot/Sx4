package com.sx4.bot.formatter.output.parser;

import com.sx4.bot.formatter.output.function.FormatterEvent;
import com.sx4.bot.formatter.output.function.FormatterFunction;

public class MultiplicationFunction extends FormatterFunction<Number> {

	public MultiplicationFunction() {
		super(Number.class, "multiply", "Multiplies both numbers together");
	}

	public Number parse(FormatterEvent<Number> event, Number number) {
		return event.getObject().doubleValue() * number.doubleValue();
	}

}
