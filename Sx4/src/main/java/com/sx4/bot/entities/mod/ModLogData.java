package com.sx4.bot.entities.mod;

import org.bson.Document;

public class ModLogData {

	private final boolean enabled;
	private final long channelId;
	
	public ModLogData(Document data) {
		this(data.getBoolean("enabled", false), data.get("channelId", 0L));
	}
	
	public ModLogData(boolean enabled, long channelId) {
		this.enabled = enabled;
		this.channelId = channelId;
	}
	
	public boolean isEnabled() {
		return this.enabled;
	}
	
	public boolean hasChannelId() {
		return this.channelId != 0L;
	}
	
	public long getChannelId() {
		return this.channelId;
	}
	
}
