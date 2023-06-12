package com.sx4.bot.formatter.output.parser;

import com.sx4.bot.formatter.output.function.FormatterEvent;
import com.sx4.bot.formatter.output.function.FormatterStaticFunction;

public class SetStaticFunction extends FormatterStaticFunction {

	public SetStaticFunction() {
		super("set", "Allows you to define your own variables");
	}

	public Object parse(FormatterEvent<Void> event, String name, Object object) {
		event.addVariable(name, object);
		return object;
	}

}
