package com.sx4.bot.formatter.parser;

import com.sx4.bot.formatter.IFormatter;
import com.sx4.bot.formatter.function.FormatterEvent;
import com.sx4.bot.formatter.function.FormatterFunction;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class MapCollectionFunction extends FormatterFunction<Collection> {

	public MapCollectionFunction() {
		super(Collection.class, "map");
	}

	public List<?> parse(FormatterEvent<Collection<?>> event, String lambda) {
		Collection<?> collection = event.getObject();

		List<Object> newList = new ArrayList<>();
		for (Object element : collection) {
			event.getManager().addVariable("this", Void.class, $ -> element);
			newList.add(IFormatter.toObject(lambda, Object.class, event.getManager()));
		}

		return newList;
	}

}
