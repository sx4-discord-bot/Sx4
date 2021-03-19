package com.sx4.bot.waiter.exception;

import com.sx4.bot.waiter.Waiter.CancelType;

public class CancelException extends RuntimeException {

	private final CancelType type;

	public CancelException(CancelType type) {
		this.type = type;
	}

	public CancelException(String message, CancelType type) {
		super(message);

		this.type = type;
	}

	public CancelType getType() {
		return this.type;
	}

}
