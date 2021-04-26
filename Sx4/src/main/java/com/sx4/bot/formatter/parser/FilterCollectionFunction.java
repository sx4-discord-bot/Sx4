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

	public List<?> parse(FormatterEvent event, String lambda) {
		Collection<?> collection = (Collection<?>) event.getObject();
		Map<String, Object> arguments = event.getArguments();

		List<Object> newList = new ArrayList<>();
		for (Object element : collection) {
			arguments.put("this", element);

			Object condition = IFormatter.toObject(lambda, Boolean.class, event.getManager());
			if (condition == null) {
				condition = false;
			}

			if ((boolean) condition) {
				newList.add(element);
			}
		}

		return newList;
	}

}
