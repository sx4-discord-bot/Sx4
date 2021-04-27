package com.sx4.bot.formatter.parser;

import com.sx4.bot.formatter.function.FormatterEvent;
import com.sx4.bot.formatter.function.FormatterFunction;

public class SetStaticFunction extends FormatterFunction<Void> {

	public SetStaticFunction() {
		super(Void.class, "set");
	}

	public Object parse(FormatterEvent event, String name, Object object) {
		event.addVariable(name, object);
		return object;
	}

}
