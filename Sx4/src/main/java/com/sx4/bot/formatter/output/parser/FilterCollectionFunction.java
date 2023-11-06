package com.sx4.bot.formatter.output.parser;

import com.sx4.bot.formatter.output.Formatter;
import com.sx4.bot.formatter.output.annotation.ExcludeFormatting;
import com.sx4.bot.formatter.output.function.FormatterEvent;
import com.sx4.bot.formatter.output.function.FormatterFunction;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public class FilterCollectionFunction extends FormatterFunction<Collection> {

	public FilterCollectionFunction() {
		super(Collection.class, "filter", "Filters a list by a condition");
	}

	public List<?> parse(FormatterEvent<Collection<?>> event, @ExcludeFormatting String lambda, Optional<String> variableName) {
		Collection<?> collection = event.getObject();
		String variable = variableName.orElse("this");

		List<Object> newList = new ArrayList<>();
		for (Object element : collection) {
			event.getManager().addVariable(variable, element);

			Object condition = Formatter.toObject(lambda, Boolean.class, event.getManager());
			if (condition == null) {
				condition = false;
			}

			if ((boolean) condition) {
				newList.add(element);
			}
		}

		event.getManager().removeVariable(variable);

		return newList;
	}

}
