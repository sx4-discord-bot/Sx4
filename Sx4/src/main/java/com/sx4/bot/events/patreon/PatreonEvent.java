package com.sx4.bot.events.patreon;

public class PatreonEvent {

	private final long discordId;
	private final String id;
	
	public PatreonEvent(long discordId, String id) {
		this.discordId = discordId;
		this.id = id;
	}
	
	public long getDiscordId() {
		return this.discordId;
	}
	
	public boolean hasDiscord() {
		return this.discordId != 0L;
	}
	
	public String getId() {
		return this.id;
	}
	
}
