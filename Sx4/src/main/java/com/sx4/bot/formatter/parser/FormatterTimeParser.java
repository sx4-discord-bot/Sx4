package com.sx4.bot.formatter.parser;

import com.sx4.bot.formatter.Formatter;

import java.time.DateTimeException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Function;

public class FormatterTimeParser implements Function<Formatter.Variable, Object> {

	private final OffsetDateTime time;

	public FormatterTimeParser(OffsetDateTime time) {
		this.time = time;
	}

	public Object apply(Formatter.Variable variable) {
		if (variable.hasTag()) {
			try {
				return this.time.format(DateTimeFormatter.ofPattern(variable.getTag()));
			} catch (DateTimeException e) {
				return this.time;
			}
		}

		return this.time;
	}

}
