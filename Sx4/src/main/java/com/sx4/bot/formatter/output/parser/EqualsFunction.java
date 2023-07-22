package com.sx4.bot.formatter.output.parser;

import com.sx4.bot.formatter.output.annotation.UsePrevious;
import com.sx4.bot.formatter.output.function.FormatterEvent;
import com.sx4.bot.formatter.output.function.FormatterFunction;

public class EqualsFunction extends FormatterFunction<Object> {

	public EqualsFunction() {
		super(Object.class, "equals", "Checks if two objects are equal");
	}

	public boolean parse(FormatterEvent<Object> event, @UsePrevious Object argument) {
		Object object = event.getObject();
		if (object instanceof Number) {
			return ((Number) object).doubleValue() == ((Number) argument).doubleValue();
		}

		return object.equals(argument);
	}

}
