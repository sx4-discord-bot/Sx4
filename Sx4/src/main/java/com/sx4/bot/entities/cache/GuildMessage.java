package com.sx4.bot.entities.cache;

public class GuildMessage {

	private final long userId;
	private final boolean pinned;
	private final String content;

	public GuildMessage(long userId, boolean pinned, String content) {
		this.userId = userId;
		this.pinned = pinned;
		this.content = content;
	}

	public boolean isPinned() {
		return this.pinned;
	}

	public long getUserId() {
		return this.userId;
	}

	public String getContent() {
		return this.content;
	}

}
