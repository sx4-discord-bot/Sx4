package com.sx4.bot.formatter.output.parser;

import com.sx4.bot.entities.info.Currency;
import com.sx4.bot.formatter.output.function.FormatterEvent;
import com.sx4.bot.formatter.output.function.FormatterFunction;

public class ConvertFunction extends FormatterFunction<Currency> {

	public ConvertFunction() {
		super(Currency.class, "convert", "Converts a currency to another");
	}

	public double parse(FormatterEvent<Currency> event, String currency) {
		return event.getObject().convert(currency);
	}

}
