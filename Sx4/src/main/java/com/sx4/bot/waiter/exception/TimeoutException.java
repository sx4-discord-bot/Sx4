package com.sx4.bot.waiter.exception;

public class TimeoutException extends RuntimeException {

	public TimeoutException() {
		super();
	}

	public TimeoutException(String message) {
		super(message);
	}

}
