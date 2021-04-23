package com.sx4.bot.formatter;

import org.bson.Document;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JsonFormatter implements IFormatter<Document> {

	private final Map<String, Object> arguments;
	private final FormatterManager manager;

	private final Document document;

	public JsonFormatter(Document document) {
		this(document, new HashMap<>());
	}

	private JsonFormatter(Document document, Map<String, Object> arguments) {
		this.document = document;
		this.arguments = arguments;
		this.manager = FormatterManager.getDefaultManager();
	}

	public JsonFormatter addArgument(String name, Object argument) {
		this.arguments.put(name, argument);

		return this;
	}

	public Document parse() {
		return this.parse(this.document, this.arguments);
	}

	public Document parse(Document json, Map<String, Object> arguments) {
		Document newJson = new Document();
		for (Map.Entry<String, Object> entry : json.entrySet()) {
			Object value = entry.getValue();
			if (value instanceof Document) {
				newJson.put(entry.getKey(), this.parse((Document) value, arguments));
			} else if (value instanceof List) {
				List<Object> newList = new ArrayList<>();
				for (Object element : (List<?>) value) {
					if (element instanceof Document) {
						newList.add(this.parse((Document) element, arguments));
					} else if (element instanceof String) {
						newList.add(this.parse((String) element, arguments, this.manager));
					} else {
						newList.add(element);
					}
				}

				newJson.put(entry.getKey(), newList);
			} else if (value instanceof String) {
				newJson.put(entry.getKey(), this.parse((String) value, arguments, this.manager));
			} else {
				newJson.put(entry.getKey(), value);
			}
		}

		return newJson;
	}

	public static JsonFormatter of(Document document, Map<String, Object> arguments) {
		return new JsonFormatter(document, arguments);
	}

}
