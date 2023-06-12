package com.sx4.bot.formatter.output.parser;

import com.sx4.bot.formatter.output.function.FormatterCondition;
import com.sx4.bot.formatter.output.function.FormatterEvent;
import com.sx4.bot.formatter.output.function.FormatterFunction;

public class ThenFunction extends FormatterFunction<Boolean> {

	public ThenFunction() {
		super(Boolean.class, "then", "The value to be returned if the condition is true");
	}

	public FormatterCondition parse(FormatterEvent<Boolean> event, Object object) {
		return new FormatterCondition(event.getObject(), object);
	}

}
