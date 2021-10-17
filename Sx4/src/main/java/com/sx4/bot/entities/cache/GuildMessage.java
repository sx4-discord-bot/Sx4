package com.sx4.bot.entities.cache;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;

public class GuildMessage {

	private final JDA jda;

	private final long authorId;
	private final long messageId;

	private final boolean pinned;
	private final String content;

	private GuildMessage(JDA jda, long messageId, long authorId, boolean pinned, String content) {
		this.jda = jda;
		this.messageId = messageId;
		this.authorId = authorId;
		this.pinned = pinned;
		this.content = content;
	}

	public boolean isPinned() {
		return this.pinned;
	}

	public long getId() {
		return this.messageId;
	}

	public long getAuthorId() {
		return this.authorId;
	}

	public User getAuthor() {
		return this.jda.getUserById(this.authorId);
	}

	public String getContent() {
		return this.content;
	}

	public static GuildMessage fromMessage(Message message) {
		return new GuildMessage(message.getJDA(), message.getIdLong(), message.getAuthor().getIdLong(), message.isPinned(), message.getContentRaw());
	}

}
