package com.sx4.bot.formatter.output.parser;

import com.sx4.bot.formatter.output.Formatter;
import com.sx4.bot.formatter.output.function.FormatterEvent;
import com.sx4.bot.formatter.output.function.FormatterFunction;

import java.util.*;

public class SortCollectionFunction extends FormatterFunction<List> {

	public SortCollectionFunction() {
		super(List.class, "sort", "Sorts a list to another object");
	}

	public List<?> parse(FormatterEvent<List<?>> event, String lambda) {
		List<?> list = event.getObject();

		list.sort((Comparator<Object>) (firstObject, secondObject) -> {
			event.getManager().addVariable("first", firstObject);
			event.getManager().addVariable("second", secondObject);

			Object value = Formatter.toObject(lambda, Integer.class, event.getManager());
			return value == null ? 0 : (int) value;
		});

		event.getManager().removeVariable("first");
		event.getManager().removeVariable("second");

		return list;
	}
}
