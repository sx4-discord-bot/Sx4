package com.sx4.bot.formatter.output.parser;

import com.sx4.bot.formatter.output.function.FormatterEvent;
import com.sx4.bot.formatter.output.function.FormatterFunction;
import org.bson.Document;

public class HasFunction extends FormatterFunction<Document> {

	public HasFunction() {
		super(Document.class, "has", "Returns true if a key exists in a json object");
	}

	public boolean parse(FormatterEvent<Document> event, String key) {
		return event.getObject().containsKey(key);
	}

}
