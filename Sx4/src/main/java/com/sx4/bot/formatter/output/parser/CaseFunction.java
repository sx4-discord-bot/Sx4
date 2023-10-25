package com.sx4.bot.formatter.output.parser;

import com.sx4.bot.formatter.output.function.FormatterEvent;
import com.sx4.bot.formatter.output.function.FormatterFunction;
import com.sx4.bot.formatter.output.function.FormatterSwitch;

public class CaseFunction extends FormatterFunction<FormatterSwitch> {

	public CaseFunction() {
		super(FormatterSwitch.class, "case", "Compares a value against the switch and returns it if it matches");
	}

	public FormatterSwitch parse(FormatterEvent<FormatterSwitch> event, Object query, Object value) {
		return event.getObject().checkCase(query, value);
	}

}
