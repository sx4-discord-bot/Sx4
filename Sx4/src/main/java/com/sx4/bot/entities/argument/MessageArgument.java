package com.sx4.bot.entities.argument;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.internal.requests.DeferredRestAction;

public class MessageArgument {
	
	private final long messageId;

	private final Message message;
	private final MessageChannel channel;

	public MessageArgument(long messageId, MessageChannel channel) {
		this.messageId = messageId;
		this.channel = channel;
		this.message = null;
	}

	public MessageArgument(Message message) {
		this.messageId = message.getIdLong();
		this.channel = message.getChannel();
		this.message = message;
	}
	
	public long getMessageId() {
		return this.messageId;
	}

	public MessageChannel getChannel() {
		return this.channel;
	}

	public GuildMessageChannel getGuildChannel() {
		return (GuildMessageChannel) this.channel;
	}

	public Message getMessage() {
		return this.message;
	}
	
	public RestAction<Message> retrieveMessage() {
		return new DeferredRestAction<>(this.channel.getJDA(), Message.class, this::getMessage, () -> this.channel.retrieveMessageById(this.messageId));
	}
	
}
