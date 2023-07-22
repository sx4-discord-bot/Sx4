package com.sx4.bot.formatter.output.parser;

import com.sx4.bot.formatter.output.annotation.UsePrevious;
import com.sx4.bot.formatter.output.function.FormatterEvent;
import com.sx4.bot.formatter.output.function.FormatterFunction;

public class CompareToFunction extends FormatterFunction<Comparable> {

	public CompareToFunction() {
		super(Comparable.class, "compareTo", "Compares two objects");
	}

	public int parse(FormatterEvent<Comparable> event, @UsePrevious Comparable<?> comparable) {
		return event.getObject().compareTo(comparable);
	}

}
