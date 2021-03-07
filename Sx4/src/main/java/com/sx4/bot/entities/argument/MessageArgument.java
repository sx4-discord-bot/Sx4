package com.sx4.bot.entities.argument;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.requests.RestAction;

public class MessageArgument {
	
	private final long messageId;
	private final TextChannel channel;

	public MessageArgument(long messageId, TextChannel channel) {
		this.messageId = messageId;
		this.channel = channel;
	}
	
	public long getMessageId() {
		return this.messageId;
	}

	public TextChannel getChannel() {
		return this.channel;
	}
	
	public RestAction<Message> retrieveMessage() {
		return this.channel.retrieveMessageById(this.messageId);
	}
	
}
