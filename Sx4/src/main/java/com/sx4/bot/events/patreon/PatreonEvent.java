package com.sx4.bot.events.patreon;

public class PatreonEvent {

	private final long discordId;
	private final int totalAmount;
	
	public PatreonEvent(long discordId, int totalAmount) {
		this.discordId = discordId;
		this.totalAmount = totalAmount;
	}
	
	public long getDiscordId() {
		return this.discordId;
	}
	
	public int getTotalAmount() {
		return this.totalAmount;
	}
	
}
