package com.sx4.bot.formatter.output.parser;

import com.sx4.bot.formatter.output.Formatter;
import com.sx4.bot.formatter.output.annotation.ExcludeFormatting;
import com.sx4.bot.formatter.output.function.FormatterEvent;
import com.sx4.bot.formatter.output.function.FormatterFunction;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public class MapCollectionFunction extends FormatterFunction<Collection> {

	public MapCollectionFunction() {
		super(Collection.class, "map", "Maps a list to another object");
	}

	public List<?> parse(FormatterEvent<Collection<?>> event, @ExcludeFormatting String lambda, Optional<String> variableName, Optional<String> indexVariableName) {
		Collection<?> collection = event.getObject();
		String variable = variableName.orElse("this");
		String indexVariable = indexVariableName.orElse("index");

		int i = 0;
		List<Object> newList = new ArrayList<>();
		for (Object element : collection) {
			event.getManager().addVariable(variable, element);
			event.getManager().addVariable(indexVariable, i++);
			newList.add(Formatter.toObject(lambda, Object.class, event.getManager()));
		}

		event.getManager().removeVariable(variable);
		event.getManager().removeVariable(indexVariable);

		return newList;
	}

}
