package com.sx4.bot.entities.argument;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.requests.RestAction;

public class MessageArgument {
	
	private final long messageId;
	private final RestAction<Message> action;

	public MessageArgument(long messageId, RestAction<Message> action) {
		this.messageId = messageId;
		this.action = action;
	}
	
	public long getMessageId() {
		return this.messageId;
	}
	
	public RestAction<Message> getRestAction() {
		return this.action;
	}
	
}
