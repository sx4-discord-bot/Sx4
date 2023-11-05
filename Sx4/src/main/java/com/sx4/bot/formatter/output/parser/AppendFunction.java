package com.sx4.bot.formatter.output.parser;

import com.sx4.bot.formatter.output.function.FormatterEvent;
import com.sx4.bot.formatter.output.function.FormatterFunction;
import org.bson.Document;

public class AppendFunction extends FormatterFunction<Document> {

	public AppendFunction() {
		super(Document.class, "append", "Appends a key and a value to the json object");
	}

	public Document parse(FormatterEvent<Document> event, String key, Object value) {
		return event.getObject().append(key, value);
	}

}
