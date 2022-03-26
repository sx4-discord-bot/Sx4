package com.sx4.bot.formatter;

import org.bson.Document;

import java.util.ArrayList;
import java.util.List;

public class JsonFormatter extends Formatter<Document> {

	public JsonFormatter(Document document, FormatterManager manager) {
		super(document, manager);
	}

	public JsonFormatter(Document document) {
		super(document);
	}

	public Document parse() {
		return this.parse(this.object);
	}

	private Document parse(Document json) {
		Document document = new Document();
		for (String key : json.keySet()) {
			Object value = json.get(key);
			if (value instanceof Document) {
				document.append(key, this.parse((Document) value));
			} else if (value instanceof Iterable) {
				List<Object> list = new ArrayList<>();
				for (Object element : (Iterable<?>) value) {
					if (element instanceof Document) {
						list.add(this.parse((Document) element));
					} else if (element instanceof String) {
						list.add(Formatter.format((String) element, this.manager));
					} else {
						list.add(element);
					}
				}

				document.append(key, list);
			} else if (value instanceof String) {
				document.append(key, Formatter.format((String) value, this.manager));
			} else {
				document.append(key, value);
			}
		}

		return document;
	}

}
