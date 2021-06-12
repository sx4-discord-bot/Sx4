package com.sx4.bot.formatter.parser;

import com.sx4.bot.formatter.function.FormatterEvent;
import com.sx4.bot.formatter.function.FormatterFunction;

public class NotEqualsFunction extends FormatterFunction<Object> {

	public NotEqualsFunction() {
		super(Object.class, "notEquals", "Checks if the two objects are not equal", true);
	}

	public boolean parse(FormatterEvent<Object> event, Object argument) {
		Object object = event.getObject();
		if (object instanceof Number) {
			return ((Number) object).doubleValue() != ((Number) argument).doubleValue();
		}

		return !object.equals(argument);
	}

}
