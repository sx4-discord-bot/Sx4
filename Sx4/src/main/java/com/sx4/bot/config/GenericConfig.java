package com.sx4.bot.config;

import com.sx4.bot.database.mongo.MongoDatabase;
import org.bson.Document;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

public class GenericConfig {

	private final String path;

	private Document json;

	public GenericConfig(String path) {
		this.path = path;

		this.reload();
	}

	public String getFilePath() {
		return this.path;
	}

	public GenericConfig replace(Document json) {
		this.json = json;

		return this;
	}

	public <Type> Type get(String path) {
		return this.get(path, (Type) null);
	}

	public <Type> Type get(String path, Class<Type> clazz) {
		return this.get(path);
	}

	public <Type> Type get(String path, Type defaultValue) {
		return this.get(Arrays.asList(path.split("\\.")), defaultValue);
	}

	@SuppressWarnings("unchecked")
	public <Type> Type get(List<String> path, Type defaultValue) {
		Document json = this.json;

		for (int i = 0; i < path.size(); i++) {
			String key = path.get(i);
			if (!json.containsKey(key)) {
				return defaultValue;
			}

			Object value = json.get(key);
			if (i == path.size() - 1) {
				return (Type) value;
			}

			if (value instanceof Document) {
				json = (Document) value;
			} else {
				return defaultValue;
			}
		}

		return defaultValue;
	}

	public GenericConfig set(String path, Object value) throws IOException {
		return this.set(path.split("\\."), value);
	}

	public GenericConfig set(String[] path, Object value) {
		return this.set(Arrays.asList(path), value);
	}

	public GenericConfig set(List<String> path, Object value) {
		Document json = this.json;

		for (int i = 0; i < path.size(); i++) {
			String key = path.get(i);
			if (i == path.size() - 1) {
				json.append(key, value);
				break;
			}

			Object oldValue = json.get(key);
			if (oldValue instanceof Document) {
				json = (Document) oldValue;
			} else {
				json.append(key, json = new Document());
			}
		}

		return this;
	}

	public void update() {
		try (FileOutputStream stream = new FileOutputStream(this.path)) {
			stream.write(this.json.toJson(MongoDatabase.PRETTY_JSON).getBytes(StandardCharsets.UTF_8));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void reload() {
		try (FileInputStream stream = new FileInputStream(this.path)) {
			this.replace(Document.parse(new String(stream.readAllBytes(), StandardCharsets.UTF_8)));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}


}
