package com.sx4.bot.formatter.parser;

import com.sx4.bot.formatter.function.FormatterEvent;
import com.sx4.bot.formatter.function.FormatterFunction;

public class NumberStaticFunction extends FormatterFunction<Void> {

	public NumberStaticFunction() {
		super(Void.class, "number");
	}

	public Double parse(FormatterEvent event, Double number) {
		return number;
	}

}
