package com.sx4.bot.formatter;

import org.bson.Document;

import java.util.ArrayList;
import java.util.List;

public class JsonFormatter implements IFormatter<Document> {

	private final FormatterManager manager;

	private final Document document;

	public JsonFormatter(Document document, FormatterManager manager) {
		this.document = document;
		this.manager = manager;
	}

	public JsonFormatter(Document document) {
		this(document, FormatterManager.getDefaultManager());
	}

	public JsonFormatter addVariable(Class<?> type, String name, Object argument) {
		this.manager.addVariable(name, type, $ -> argument);

		return this;
	}

	public JsonFormatter addVariable(String name, Object argument) {
		this.manager.addVariable(name, Void.class, $ -> argument);

		return this;
	}

	public Document parse() {
		return this.parse(this.document);
	}

	public Document parse(Document json) {
		for (String key : json.keySet()) {
			Object value = json.get(key);
			if (value instanceof Document) {
				json.put(key, this.parse((Document) value));
			} else if (value instanceof Iterable) {
				List<Object> list = new ArrayList<>();
				for (Object element : (Iterable<?>) value) {
					if (element instanceof Document) {
						list.add(this.parse((Document) element));
					} else if (element instanceof String) {
						list.add(this.parse((String) element, this.manager));
					} else {
						list.add(element);
					}
				}

				json.put(key, list);
			} else if (value instanceof String) {
				json.put(key, this.parse((String) value, this.manager));
			}
		}

		return json;
	}

}
