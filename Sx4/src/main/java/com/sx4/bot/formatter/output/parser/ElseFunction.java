package com.sx4.bot.formatter.output.parser;

import com.sx4.bot.formatter.output.function.FormatterCondition;
import com.sx4.bot.formatter.output.function.FormatterEvent;
import com.sx4.bot.formatter.output.function.FormatterFunction;

public class ElseFunction extends FormatterFunction<FormatterCondition> {

	public ElseFunction() {
		super(FormatterCondition.class, "else", "The value to be returned if the condition is false");
	}

	public Object parse(FormatterEvent<FormatterCondition> event, Object object) {
		return event.getObject().orElse(object);
	}

}
