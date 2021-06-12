package com.sx4.bot.formatter.parser;

import com.sx4.bot.formatter.function.FormatterEvent;
import com.sx4.bot.formatter.function.FormatterStaticFunction;

public class SetStaticFunction extends FormatterStaticFunction {

	public SetStaticFunction() {
		super("set", "Allows you to define your own variables");
	}

	public Object parse(FormatterEvent<Void> event, String name, Object object) {
		event.addVariable(name, object);
		return object;
	}

}
