package com.sx4.bot.formatter.output.parser;

import com.sx4.bot.formatter.output.function.FormatterEvent;
import com.sx4.bot.formatter.output.function.FormatterStaticFunction;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class RangeFunction extends FormatterStaticFunction {

	public RangeFunction() {
		super("range", "Gives a list of numbers based on start and end index");
	}

	public List<Integer> parse(FormatterEvent<Void> event, Integer first, Optional<Integer> second) {
		int start = second.isPresent() ? first : 0;
		int end = second.orElse(first);

		List<Integer> range = new ArrayList<>();
		for (int i = start; i < end; i++) {
			range.add(i);
		}

		return range;
	}

}
