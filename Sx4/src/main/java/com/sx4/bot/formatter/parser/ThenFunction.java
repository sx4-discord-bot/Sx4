package com.sx4.bot.formatter.parser;

import com.sx4.bot.formatter.function.FormatterCondition;
import com.sx4.bot.formatter.function.FormatterEvent;
import com.sx4.bot.formatter.function.FormatterFunction;

public class ThenFunction extends FormatterFunction<Boolean> {

	public ThenFunction() {
		super(Boolean.class, "then");
	}

	public FormatterCondition parse(FormatterEvent<Boolean> event, Object object) {
		return new FormatterCondition(event.getObject(), object);
	}

}
