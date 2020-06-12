package com.sx4.bot.events.patreon;

public class PatreonPledgeUpdateEvent extends PatreonPledgeCreateEvent {

	public PatreonPledgeUpdateEvent(long discordId, String id, int amount) {
		super(discordId, id, amount);
	}
	
}
