package com.sx4.bot.formatter.output.parser;

import com.sx4.bot.formatter.output.function.FormatterEvent;
import com.sx4.bot.formatter.output.function.FormatterFunction;
import org.bson.Document;

import java.util.Optional;

public class GetObjectFunction extends FormatterFunction<Document> {

	public GetObjectFunction() {
		super(Document.class, "get", "Gets an object from a json object");
	}

	public Object parse(FormatterEvent<Document> event, String key, Optional<Object> defaultValue) {
		Object object = event.getObject().get(key);
		if (object == null) {
			return defaultValue.orElse(null);
		}

		return object;
	}

}
