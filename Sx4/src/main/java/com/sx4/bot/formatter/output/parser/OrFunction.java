package com.sx4.bot.formatter.output.parser;

import com.sx4.bot.formatter.output.function.FormatterEvent;
import com.sx4.bot.formatter.output.function.FormatterFunction;

public class OrFunction extends FormatterFunction<Boolean> {

	public OrFunction() {
		super(Boolean.class, "or", "Combines with another boolean and returns true if either are true otherwise false");
	}

	public Boolean parse(FormatterEvent<Boolean> event, Boolean bool) {
		return bool || event.getObject();
	}

}
