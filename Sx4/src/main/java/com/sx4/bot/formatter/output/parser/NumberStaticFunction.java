package com.sx4.bot.formatter.output.parser;

import com.sx4.bot.formatter.output.function.FormatterEvent;
import com.sx4.bot.formatter.output.function.FormatterStaticFunction;

public class NumberStaticFunction extends FormatterStaticFunction {

	public NumberStaticFunction() {
		super("number", "Allows you to define a number without a variable");
	}

	public Double parse(FormatterEvent<Void> event, Double number) {
		return number;
	}

}
