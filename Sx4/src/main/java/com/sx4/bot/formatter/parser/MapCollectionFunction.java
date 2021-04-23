package com.sx4.bot.formatter.parser;

import com.sx4.bot.formatter.IFormatter;
import com.sx4.bot.formatter.function.FormatterEvent;
import com.sx4.bot.formatter.function.FormatterFunction;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class MapCollectionFunction extends FormatterFunction<Collection> {

	public MapCollectionFunction() {
		super(Collection.class, "map");
	}

	public List<?> parse(FormatterEvent event, String lambda) {
		Map<String, Object> arguments = event.getArguments();
		Collection<?> collection = (Collection<?>) event.getObject();

		List<Object> newList = new ArrayList<>();
		for (Object element : collection) {
			arguments.put("this", element);
			newList.add(IFormatter.format(lambda, arguments, event.getManager()));
		}

		return newList;
	}

}
