package com.sx4.bot.waiter.exception;

import com.sx4.bot.waiter.Waiter.CancelType;
import net.dv8tion.jda.api.events.GenericEvent;

public class CancelException extends RuntimeException {

	private final GenericEvent event;
	private final CancelType type;

	public CancelException(GenericEvent event, CancelType type) {
		this.type = type;
		this.event = event;
	}

	public CancelException(String message, GenericEvent event, CancelType type) {
		super(message);

		this.event = event;
		this.type = type;
	}

	public GenericEvent getEvent() {
		return this.event;
	}

	public CancelType getType() {
		return this.type;
	}

}
