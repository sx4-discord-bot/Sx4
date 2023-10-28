package com.sx4.bot.formatter.output.parser;

import com.sx4.bot.formatter.output.Formatter;
import com.sx4.bot.formatter.output.function.FormatterEvent;
import com.sx4.bot.formatter.output.function.FormatterFunction;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class MapCollectionFunction extends FormatterFunction<Collection> {

	public MapCollectionFunction() {
		super(Collection.class, "map", "Maps a list to another object");
	}

	public List<?> parse(FormatterEvent<Collection<?>> event, String lambda) {
		Collection<?> collection = event.getObject();

		int i = 0;
		List<Object> newList = new ArrayList<>();
		for (Object element : collection) {
			event.getManager().addVariable("this", element);
			event.getManager().addVariable("index", i++);
			newList.add(Formatter.toObject(lambda, Object.class, event.getManager()));
		}

		event.getManager().removeVariable("this");
		event.getManager().removeVariable("index");

		return newList;
	}

}
