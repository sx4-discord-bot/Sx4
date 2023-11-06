package com.sx4.bot.formatter.output.parser;

import com.sx4.bot.formatter.output.Formatter;
import com.sx4.bot.formatter.output.annotation.ExcludeFormatting;
import com.sx4.bot.formatter.output.function.FormatterEvent;
import com.sx4.bot.formatter.output.function.FormatterFunction;

import java.util.List;

public class ReduceListFunction extends FormatterFunction<List> {

	public ReduceListFunction() {
		super(List.class, "reduce", "Reduces a list to an object");
	}

	public Object parse(FormatterEvent<List<?>> event, @ExcludeFormatting String lambda) {
		List<?> list = event.getObject();
		if (list.isEmpty()) {
			return null;
		}

		Object reduced = list.get(0);
		for (int i = 1; i < list.size(); i++) {
			Object element = list.get(i);
			event.getManager().addVariable("value", reduced);
			event.getManager().addVariable("this", element);

			reduced = Formatter.toObject(lambda, Object.class, event.getManager());
		}

		event.getManager().removeVariable("value");
		event.getManager().removeVariable("this");

		return reduced;
	}

}
