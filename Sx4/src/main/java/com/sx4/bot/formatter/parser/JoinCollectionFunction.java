package com.sx4.bot.formatter.parser;

import com.sx4.bot.formatter.function.FormatterEvent;
import com.sx4.bot.formatter.function.FormatterFunction;

import java.util.Collection;
import java.util.StringJoiner;

public class JoinCollectionFunction extends FormatterFunction<Collection> {

	public JoinCollectionFunction() {
		super(Collection.class, "join", "Joins a list by a delimiter");
	}

	public String parse(FormatterEvent<Collection<?>> event, String delimiter) {
		Collection<?> collection = event.getObject();

		StringJoiner joiner = new StringJoiner(delimiter);
		for (Object element : collection) {
			joiner.add(element.toString());
		}

		return joiner.toString();
	}

}
