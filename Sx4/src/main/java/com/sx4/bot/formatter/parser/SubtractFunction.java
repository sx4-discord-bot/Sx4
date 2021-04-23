package com.sx4.bot.formatter.parser;

import com.sx4.bot.formatter.function.FormatterEvent;
import com.sx4.bot.formatter.function.FormatterFunction;

public class SubtractFunction extends FormatterFunction<Number> {

	public SubtractFunction() {
		super(Number.class, "subtract");
	}

	public Number parse(FormatterEvent event, Number number) {
		Number argument = ((Number) event.getObject());
		return (argument instanceof Double ? argument.doubleValue() : argument instanceof Integer ? argument.intValue() : argument.longValue()) - (number instanceof Double ? number.doubleValue() : number instanceof Integer ? number.intValue() : number.longValue());
	}

}
