package com.sx4.bot.formatter.output.parser;

import com.sx4.bot.formatter.output.function.FormatterEvent;
import com.sx4.bot.formatter.output.function.FormatterFunction;
import com.sx4.bot.formatter.output.function.FormatterSwitch;

public class DefaultFunction extends FormatterFunction<FormatterSwitch> {

	public DefaultFunction() {
		super(FormatterSwitch.class, "default", "Sets a default value for the switch");
	}

	public FormatterSwitch parse(FormatterEvent<FormatterSwitch> event, Object defaultValue) {
		return event.getObject().addDefault(defaultValue);
	}

}
