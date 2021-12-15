package com.sx4.bot.formatter.parser;

import com.sx4.bot.entities.info.Currency;
import com.sx4.bot.formatter.function.FormatterEvent;
import com.sx4.bot.formatter.function.FormatterFunction;

public class ConvertFunction extends FormatterFunction<Currency> {

	public ConvertFunction() {
		super(Currency.class, "convert", "Converts a currency to another");
	}

	public double parse(FormatterEvent<Currency> event, String currency) {
		return event.getObject().convert(currency);
	}

}
