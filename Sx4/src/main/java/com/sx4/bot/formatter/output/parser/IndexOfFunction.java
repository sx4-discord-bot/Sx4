package com.sx4.bot.formatter.output.parser;

import com.sx4.bot.formatter.output.function.FormatterEvent;
import com.sx4.bot.formatter.output.function.FormatterFunction;

public class IndexOfFunction extends FormatterFunction<String> {

	public IndexOfFunction() {
		super(String.class, "indexOf", "Gets the index of text within another segment of text or -1 if it isn't included");
	}

	public Integer parse(FormatterEvent<String> event, String text) {
		return event.getObject().indexOf(text);
	}

}
