package com.sx4.bot.formatter.parser;

import com.sx4.bot.formatter.Formatter;
import com.sx4.bot.formatter.function.FormatterEvent;
import com.sx4.bot.formatter.function.FormatterFunction;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class FilterCollectionFunction extends FormatterFunction<Collection> {

	public FilterCollectionFunction() {
		super(Collection.class, "filter", "Filters a list by a condition");
	}

	public List<?> parse(FormatterEvent<Collection<?>> event, String lambda) {
		Collection<?> collection = event.getObject();

		List<Object> newList = new ArrayList<>();
		for (Object element : collection) {
			event.getManager().addVariable("this", element);

			Object condition = Formatter.toObject(lambda, Boolean.class, event.getManager());
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
