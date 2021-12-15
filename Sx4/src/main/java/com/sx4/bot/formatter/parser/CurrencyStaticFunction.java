package com.sx4.bot.formatter.parser;

import com.sx4.bot.entities.info.Currency;
import com.sx4.bot.formatter.function.FormatterEvent;
import com.sx4.bot.formatter.function.FormatterStaticFunction;

public class CurrencyStaticFunction extends FormatterStaticFunction {

	public CurrencyStaticFunction() {
		super("currency", "Allows you to create a currency instance");
	}

	public Currency parse(FormatterEvent<Void> event, Double amount, String currency) {
		return new Currency(amount, currency);
	}

}
