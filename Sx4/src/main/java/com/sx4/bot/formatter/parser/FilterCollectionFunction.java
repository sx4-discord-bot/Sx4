package com.sx4.bot.formatter.parser;

import com.sx4.bot.formatter.IFormatter;
import com.sx4.bot.formatter.function.FormatterEvent;
import com.sx4.bot.formatter.function.FormatterFunction;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class FilterCollectionFunction extends FormatterFunction<Collection> {

	public FilterCollectionFunction() {
		super(Collection.class, "filter");
	}

	public List<?> parse(FormatterEvent<Collection<?>> event, String lambda) {
		Collection<?> collection = event.getObject();

		List<Object> newList = new ArrayList<>();
		for (Object element : collection) {
			event.getManager().addVariable("this", Void.class, $ -> element);

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
