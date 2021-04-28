package com.sx4.bot.formatter;

import org.bson.Document;

import java.util.*;

public class JsonFormatter implements IFormatter<Document> {

	private final FormatterManager manager;

	private final Document document;

	public JsonFormatter(Document document) {
		this.document = document;
		this.manager = new FormatterManager(FormatterManager.getDefaultManager());
	}

	public JsonFormatter addVariable(String name, Object argument) {
		this.manager.addVariable(name, Void.class, $ -> argument);

		return this;
	}

	public Document parse() {
		return this.parse(this.document);
	}

	public Document parse(Document json) {
		Document newJson = new Document();
		for (Map.Entry<String, Object> entry : json.entrySet()) {
			Object value = entry.getValue();
			if (value instanceof Document) {
				newJson.put(entry.getKey(), this.parse((Document) value));
			} else if (value instanceof Iterable) {
				List<Object> newList = new ArrayList<>();
				for (Object element : (Iterable<?>) value) {
					if (element instanceof Document) {
						newList.add(this.parse((Document) element));
					} else if (element instanceof String) {
						newList.add(this.parse((String) element, this.manager));
					} else {
						newList.add(element);
					}
				}

				newJson.put(entry.getKey(), newList);
			} else if (value instanceof String) {
				newJson.put(entry.getKey(), this.parse((String) value, this.manager));
			} else {
				newJson.put(entry.getKey(), value);
			}
		}

		return newJson;
	}

}
