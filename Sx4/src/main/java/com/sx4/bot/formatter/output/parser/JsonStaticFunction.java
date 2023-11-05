package com.sx4.bot.formatter.output.parser;

import com.sx4.bot.formatter.output.function.FormatterEvent;
import com.sx4.bot.formatter.output.function.FormatterStaticFunction;
import org.bson.Document;

public class JsonStaticFunction extends FormatterStaticFunction {

	public JsonStaticFunction() {
		super("json", "Creates a json object with a key and value");
	}

	public Document parse(FormatterEvent<Void> event, String key, Object value) {
		return new Document(key, value);
	}

}
