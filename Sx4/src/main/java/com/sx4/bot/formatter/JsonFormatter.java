package com.sx4.bot.formatter;

import org.bson.Document;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class JsonFormatter implements Formatter<Document> {

	private final Map<String, Function<Variable, Object>> map;
	private final Document document;

	public JsonFormatter(Document document) {
		this(document, new HashMap<>());
	}

	private JsonFormatter(Document document, Map<String, Function<Variable, Object>> map) {
		this.document = document;
		this.map = map;
	}

	public JsonFormatter appendFunction(String key, Function<Variable, Object> function) {
		this.map.put(key, function);

		return this;
	}

	public Document parse() {
		return this.parse(this.document, this.map);
	}

	public Document parse(Document json, Map<String, Function<Variable, Object>> map) {
		Document newJson = new Document();
		for (Map.Entry<String, Object> entry : json.entrySet()) {
			Object value = entry.getValue();
			if (value instanceof Document) {
				newJson.put(entry.getKey(), this.parse((Document) value, map));
			} else if (value instanceof List) {
				List<Object> newList = new ArrayList<>();
				for (Object element : (List<?>) value) {
					if (element instanceof Document) {
						newList.add(this.parse((Document) element, map));
					} else if (element instanceof String) {
						newList.add(this.parse((String) element, map));
					} else {
						newList.add(element);
					}
				}

				newJson.put(entry.getKey(), newList);
			} else if (value instanceof String) {
				newJson.put(entry.getKey(), this.parse((String) value, map));
			} else {
				newJson.put(entry.getKey(), value);
			}
		}

		return newJson;
	}

	public static JsonFormatter of(Document document, Map<String, Function<Variable, Object>> map) {
		return new JsonFormatter(document, map);
	}

}
