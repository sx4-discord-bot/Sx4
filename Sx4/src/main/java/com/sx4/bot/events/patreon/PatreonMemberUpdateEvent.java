package com.sx4.bot.events.patreon;

public class PatreonMemberUpdateEvent extends PatreonMemberEvent {

	public PatreonMemberUpdateEvent(long discordId, String id) {
		super(discordId, id);
	}
	
}
