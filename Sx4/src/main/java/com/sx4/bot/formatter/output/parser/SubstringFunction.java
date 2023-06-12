package com.sx4.bot.formatter.output.parser;

import com.sx4.bot.formatter.output.function.FormatterEvent;
import com.sx4.bot.formatter.output.function.FormatterFunction;
import com.sx4.bot.utility.StringUtility;

public class SubstringFunction extends FormatterFunction<String> {

	public SubstringFunction() {
		super(String.class, "substring", "Cuts of a string by the indexes given");
	}

	public String parse(FormatterEvent<String> event, Integer start, Integer end) {
		return StringUtility.substring(event.getObject(), start, end);
	}

}
