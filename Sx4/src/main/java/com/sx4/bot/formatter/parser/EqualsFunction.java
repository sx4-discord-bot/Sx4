package com.sx4.bot.formatter.parser;

import com.sx4.bot.formatter.function.FormatterEvent;
import com.sx4.bot.formatter.function.FormatterFunction;

public class EqualsFunction extends FormatterFunction<Object> {

	public EqualsFunction() {
		super(Object.class, "equals", true);
	}

	public boolean parse(FormatterEvent event, Object argument) {
		Object object = event.getObject();
		if (object instanceof Number) {
			try {
				return ((Number) object).doubleValue() == ((Number) argument).doubleValue();
			} catch (NumberFormatException e) {
				return false;
			}
		}

		return object.equals(argument);
	}

}
