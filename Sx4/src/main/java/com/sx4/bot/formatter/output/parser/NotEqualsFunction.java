package com.sx4.bot.formatter.output.parser;

import com.sx4.bot.formatter.output.annotation.UsePrevious;
import com.sx4.bot.formatter.output.function.FormatterEvent;
import com.sx4.bot.formatter.output.function.FormatterFunction;

public class NotEqualsFunction extends FormatterFunction<Object> {

	public NotEqualsFunction() {
		super(Object.class, "notEquals", "Checks if the two objects are not equal");
	}

	public boolean parse(FormatterEvent<Object> event, @UsePrevious Object argument) {
		Object object = event.getObject();
		if (object instanceof Number) {
			return ((Number) object).doubleValue() != ((Number) argument).doubleValue();
		}

		return !object.equals(argument);
	}

}
