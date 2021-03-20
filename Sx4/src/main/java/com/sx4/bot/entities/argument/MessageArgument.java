package com.sx4.bot.entities.argument;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.internal.requests.DeferredRestAction;

public class MessageArgument {
	
	private final long messageId;

	private final Message message;
	private final TextChannel channel;

	public MessageArgument(long messageId, TextChannel channel) {
		this.messageId = messageId;
		this.channel = channel;
		this.message = null;
	}

	public MessageArgument(Message message) {
		this.messageId = message.getIdLong();
		this.channel = message.getTextChannel();
		this.message = message;
	}
	
	public long getMessageId() {
		return this.messageId;
	}

	public TextChannel getChannel() {
		return this.channel;
	}

	public Message getMessage() {
		return this.message;
	}
	
	public RestAction<Message> retrieveMessage() {
		return new DeferredRestAction<>(this.channel.getJDA(), Message.class, this::getMessage, () -> this.channel.retrieveMessageById(this.messageId));
	}
	
}
