package com.sx4.bot.formatter.output.function;

import okhttp3.Response;
import org.bson.Document;

import java.io.IOException;

public class FormatterResponse {

	private final int status;

	private final String body;
	private Document json;

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

	public synchronized Document asJson() {
		if (this.json == null) {
			return this.json = Document.parse(this.body);
		}

		return this.json;
	}

}
