package com.sx4.bot.formatter.output.parser;

import com.sx4.bot.formatter.output.function.FormatterEvent;
import com.sx4.bot.formatter.output.function.FormatterStaticFunction;

public class ExistsStaticFunction extends FormatterStaticFunction {

	public ExistsStaticFunction() {
		super("exists", "Returns true if a variable exists otherwise false");
	}

	public Boolean parse(FormatterEvent<Void> event, String variable) {
		return event.getManager().getVariable(Void.class, variable) != null;
	}

}
