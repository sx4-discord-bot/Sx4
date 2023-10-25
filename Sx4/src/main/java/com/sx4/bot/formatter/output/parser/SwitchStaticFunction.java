package com.sx4.bot.formatter.output.parser;

import com.sx4.bot.formatter.output.function.FormatterEvent;
import com.sx4.bot.formatter.output.function.FormatterStaticFunction;
import com.sx4.bot.formatter.output.function.FormatterSwitch;

public class SwitchStaticFunction extends FormatterStaticFunction {

	public SwitchStaticFunction() {
		super("switch", "Creates a switch instance");
	}

	public FormatterSwitch parse(FormatterEvent<Void> event, Object value) {
		return new FormatterSwitch(value);
	}

}
