package com.sx4.bot.formatter.parser;

import com.sx4.bot.formatter.IFormatter;
import com.sx4.bot.formatter.function.FormatterEvent;
import com.sx4.bot.formatter.function.FormatterFunction;

import java.util.Collection;
import java.util.Map;

public class SumListFunction extends FormatterFunction<Collection> {

	public SumListFunction() {
		super(Collection.class, "sum");
	}

	public long parse(FormatterEvent event, String lambda) {
		Collection<?> collection = (Collection<?>) event.getObject();
		Map<String, Object> arguments = event.getArguments();

		long value = 0L;
		for (Object element : collection) {
			long number;
			try {
				if (lambda.isEmpty()) {
					number = element instanceof Long ? (long) element : element instanceof String ? Long.parseLong((String) element) : 0L;
				} else {
					arguments.put("this", element);
					number = Long.parseLong(IFormatter.format(lambda, arguments, event.getManager()));
				}
			} catch (NumberFormatException e) {
				number = 0;
			}

			value += number;
		}

		return value;
	}

}
