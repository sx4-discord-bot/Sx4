package com.sx4.bot.exceptions.argument;

public class Sx4ArgumentException extends RuntimeException {

	private final String message;

	public Sx4ArgumentException(String message) {
		this.message = message;
	}

	public String getMessage() {
		return this.message;
	}

}
