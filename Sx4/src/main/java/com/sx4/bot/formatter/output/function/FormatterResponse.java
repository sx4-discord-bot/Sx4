package com.sx4.bot.formatter.output.function;

import okhttp3.Response;
import org.bson.Document;

import java.io.IOException;
import java.util.List;

public class FormatterResponse {

	private final int status;

	private final byte[] bytes;
	private Document json;
	private List<Object> array;
	private String body;

	public FormatterResponse(Response response) throws IOException {
		this.status = response.code();
		this.bytes = response.peekBody(10_000_000).bytes();
	}

	public int getStatus() {
		return this.status;
	}

	public byte[] getRaw() {
		return this.bytes;
	}

	public synchronized String asBody() {
		if (this.body == null) {
			return this.body = new String(this.bytes);
		}

		return this.body;
	}

	public synchronized List<Object> asArray() {
		String body = "{\"a\":" + this.asBody() + "}";
		if (this.array == null) {
			return this.array = Document.parse(body).getList("a", Object.class);
		}

		return this.array;
	}

	public synchronized Document asJson() {
		if (this.json == null) {
			return this.json = Document.parse(this.asBody());
		}

		return this.json;
	}

}
