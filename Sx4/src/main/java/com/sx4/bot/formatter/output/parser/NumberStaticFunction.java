package com.sx4.bot.formatter.output.parser;

import com.sx4.bot.formatter.output.function.FormatterEvent;
import com.sx4.bot.formatter.output.function.FormatterStaticFunction;

import java.util.Optional;

public class NumberStaticFunction extends FormatterStaticFunction {

	public NumberStaticFunction() {
		super("number", "Allows you to define a number without a variable");
	}

	public Double parse(FormatterEvent<Void> event, String number, Optional<Integer> radix) {
		try {
			if (number.contains(".")) {
				return Double.parseDouble(number);
			} else {
				return (double) Long.parseLong(number, radix.orElse(10));
			}
		} catch (NumberFormatException e) {
			return null;
		}
	}

}
