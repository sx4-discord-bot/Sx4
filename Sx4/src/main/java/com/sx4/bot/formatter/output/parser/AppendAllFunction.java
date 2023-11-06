package com.sx4.bot.formatter.output.parser;

import com.sx4.bot.formatter.output.function.FormatterEvent;
import com.sx4.bot.formatter.output.function.FormatterFunction;
import org.bson.Document;

public class AppendAllFunction extends FormatterFunction<Document> {

	public AppendAllFunction() {
		super(Document.class, "appendAll", "Appends all json objects keys and values to the current json object");
	}

	public Document parse(FormatterEvent<Document> event, Document data) {
		Document json = event.getObject();
		json.putAll(data);
		return json;
	}

}
