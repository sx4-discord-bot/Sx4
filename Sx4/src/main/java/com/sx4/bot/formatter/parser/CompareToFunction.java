package com.sx4.bot.formatter.parser;

import com.sx4.bot.formatter.function.FormatterEvent;
import com.sx4.bot.formatter.function.FormatterFunction;

public class CompareToFunction extends FormatterFunction<Comparable> {

	public CompareToFunction() {
		super(Comparable.class, "compareTo", "Compares two objects", true);
	}

	public int parse(FormatterEvent<Comparable> event, Comparable<?> comparable) {
		return event.getObject().compareTo(comparable);
	}

}
