package com.sx4.bot.formatter.function;

import org.bson.Document;

public class FormatterResponseBody {

	private final String body;

	public FormatterResponseBody(String body) {
		this.body = body;
	}

	public Document asJson() {
		return Document.parse(this.body);
	}

}
