package com.sx4.bot.formatter.parser;

import com.sx4.bot.formatter.function.FormatterEvent;
import com.sx4.bot.formatter.function.FormatterFunction;

import java.util.List;

public class GetListFunction extends FormatterFunction<List> {

	public GetListFunction() {
		super(List.class, "get");
	}

	public Object parse(FormatterEvent event, Integer index) {
		List<?> list = (List<?>) event.getObject();

		int size = list.size();
		if (index < 0) {
			index = size + index;
		}

		return list.get(Math.max(0, index));
	}

}
