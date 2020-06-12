package com.sx4.bot.events.patreon;

public class PatreonPledgeCreateEvent extends PatreonPledgeEvent {
	
	private final int amount;

	public PatreonPledgeCreateEvent(long discordId, String id, int amount) {
		super(discordId, id);
		
		this.amount = amount;
	}
	
	public int getAmount() {
		return this.amount;
	}
	
}
