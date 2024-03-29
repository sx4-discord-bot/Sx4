package com.sx4.bot.formatter.output.parser;

import com.sx4.bot.formatter.output.annotation.AcceptNull;
import com.sx4.bot.formatter.output.function.FormatterEvent;
import com.sx4.bot.formatter.output.function.FormatterFunction;

public class AndFunction extends FormatterFunction<Boolean> {

	public AndFunction() {
		super(Boolean.class, "and", "Combines with another boolean and returns true if both are true otherwise false");
	}

	public Boolean parse(FormatterEvent<Boolean> event, @AcceptNull Boolean bool) {
		return bool != null && bool && event.getObject();
	}

}
