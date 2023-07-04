package com.sx4.bot.formatter.output.function;

import okhttp3.Response;
import org.bson.Document;

import java.io.IOException;
import java.util.List;

public class FormatterResponse {

	private final int status;

	private final String body;
	private Document json;
	private List<Object> array;

	public FormatterResponse(Response response) throws IOException {
		this.status = response.code();
		this.body = response.peekBody(10_000_000).string();
	}

	public int getStatus() {
		return this.status;
	}

	public String getRaw() {
		return this.body;
	}

	public synchronized List<Object> asArray() {
		String body = "{\"a\":" + this.body + "}";
		if (this.array == null) {
			return this.array = Document.parse(body).getList("a", Object.class);
		}

		return this.array;
	}

	public synchronized Document asJson() {
		if (this.json == null) {
			return this.json = Document.parse(this.body);
		}

		return this.json;
	}

}
