package com.sx4.bot.formatter.output.parser;

import com.sx4.bot.formatter.output.function.FormatterEvent;
import com.sx4.bot.formatter.output.function.FormatterFunction;
import com.sx4.bot.formatter.output.function.FormatterVariable;

public class ExistsFunction extends FormatterFunction<Object> {

	public ExistsFunction() {
		super(Object.class, "exists", "Returns true if a variable exists on the current object otherwise false");
	}

	public boolean parse(FormatterEvent<Object> event, String variableName) {
		Object object = event.getObject();

		FormatterVariable<?> variable = event.getManager().getVariable(object.getClass(), variableName);
		if (variable == null) {
			return false;
		}

		return variable.parse(object) != null;
	}

}
