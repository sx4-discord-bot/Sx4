package com.sx4.bot.formatter.output.parser;

import com.sx4.bot.formatter.output.function.FormatterEvent;
import com.sx4.bot.formatter.output.function.FormatterFunction;
import org.bson.Document;

public class GetObjectFunction extends FormatterFunction<Document> {

	public GetObjectFunction() {
		super(Document.class, "get", "Gets an object from a json object");
	}

	public Object parse(FormatterEvent<Document> event, String key) {
		return event.getObject().get(key);
	}

}
