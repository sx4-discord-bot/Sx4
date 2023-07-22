package com.sx4.bot.formatter.output.parser;

import com.sx4.bot.formatter.output.function.FormatterEvent;
import com.sx4.bot.formatter.output.function.FormatterFunction;

import java.util.List;
import java.util.Optional;

public class GetListFunction extends FormatterFunction<List> {

	public GetListFunction() {
		super(List.class, "get", "Gets an element in a list by a specific index");
	}

	public Object parse(FormatterEvent<List<?>> event, Integer index, Optional<Object> defaultValue) {
		List<?> list = event.getObject();

		int size = list.size();
		if (index < 0) {
			index += size;
		}

		if (index >= size || index < 0) {
			return defaultValue.orElse(null);
		}

		return list.get(index);
	}

}
