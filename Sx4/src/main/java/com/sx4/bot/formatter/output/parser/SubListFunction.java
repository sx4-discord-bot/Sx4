package com.sx4.bot.formatter.output.parser;

import com.sx4.bot.formatter.output.function.FormatterEvent;
import com.sx4.bot.formatter.output.function.FormatterFunction;

import java.util.List;

public class SubListFunction extends FormatterFunction<List> {

	public SubListFunction() {
		super(List.class, "subList", "Cuts off a list by the indexes provided");
	}

	public List<?> parse(FormatterEvent<List<?>> event, Integer start, Integer end) {
		List<?> list = event.getObject();
		if (list.isEmpty()) {
			return list;
		}

		int size = list.size();
		if (start < 0) {
			start += size;
		}

		if (end < 0) {
			end += size;
		}

		return list.subList(Math.max(0, start), Math.min(size, end));
	}

}
