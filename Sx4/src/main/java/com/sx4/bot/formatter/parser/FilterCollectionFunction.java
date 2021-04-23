package com.sx4.bot.formatter.parser;

import com.sx4.bot.formatter.IFormatter;
import com.sx4.bot.formatter.function.FormatterEvent;
import com.sx4.bot.formatter.function.FormatterFunction;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class FilterCollectionFunction extends FormatterFunction<Collection> {

	public FilterCollectionFunction() {
		super(Collection.class, "filter");
	}

	public List<?> parse(FormatterEvent event, String condition) {
		Collection<?> collection = (Collection<?>) event.getObject();
		Map<String, Object> arguments = event.getArguments();

		List<Object> newList = new ArrayList<>();
		for (Object element : collection) {
			arguments.put("this", element);

			if (IFormatter.condition(IFormatter.format(condition, arguments, event.getManager()))) {
				newList.add(element);
			}
		}

		return newList;
	}

}
