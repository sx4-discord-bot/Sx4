package com.sx4.bot.formatter.output.parser;

import com.sx4.bot.formatter.output.function.FormatterEvent;
import com.sx4.bot.formatter.output.function.FormatterFunction;

public class DivisionFunction extends FormatterFunction<Number> {

	public DivisionFunction() {
		super(Number.class, "divide", "Divides one number by another");
	}

	public Number parse(FormatterEvent<Number> event, Number number) {
		return event.getObject().doubleValue() / number.doubleValue();
	}

}
