package com.sx4.bot.formatter.output.parser;

import com.sx4.bot.entities.info.Currency;
import com.sx4.bot.formatter.output.function.FormatterEvent;
import com.sx4.bot.formatter.output.function.FormatterStaticFunction;

public class CurrencyStaticFunction extends FormatterStaticFunction {

	public CurrencyStaticFunction() {
		super("currency", "Allows you to create a currency instance");
	}

	public Currency parse(FormatterEvent<Void> event, Double amount, String currency) {
		return new Currency(amount, currency);
	}

}
