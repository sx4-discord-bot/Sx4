package com.sx4.bot.formatter.parser;

import com.sx4.bot.formatter.Formatter;

import java.security.SecureRandom;
import java.util.function.Function;

public class FormatterRandomParser implements Function<Formatter.Variable, Object> {

	private final SecureRandom random = new SecureRandom();

	public Object apply(Formatter.Variable variable) {
		if (variable.hasTag()) {
			int bound;
			try {
				bound = Integer.parseInt(variable.getTag()) + 1;
			} catch (NumberFormatException e) {
				bound = 2;
			}

			return this.random.nextInt(bound);
		}

		return this.random.nextInt(2);
	}

}
