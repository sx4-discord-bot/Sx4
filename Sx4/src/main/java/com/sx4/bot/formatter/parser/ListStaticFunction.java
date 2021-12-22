package com.sx4.bot.formatter.parser;

import com.sx4.bot.formatter.function.FormatterEvent;
import com.sx4.bot.formatter.function.FormatterStaticFunction;

import java.util.Collection;

public class ListStaticFunction extends FormatterStaticFunction {

	public ListStaticFunction() {
		super("list", "Initiates a list object");
	}

	public Collection<Object> parse(FormatterEvent<Void> event, Collection<Object> collection) {
		return collection;
	}

}
