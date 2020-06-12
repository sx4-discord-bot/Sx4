package com.sx4.bot.hooks.patreon;

import java.util.EventListener;

import com.sx4.bot.events.patreon.PatreonPledgeCreateEvent;
import com.sx4.bot.events.patreon.PatreonPledgeDeleteEvent;
import com.sx4.bot.events.patreon.PatreonPledgeEvent;
import com.sx4.bot.events.patreon.PatreonPledgeUpdateEvent;

public interface PatreonListener extends EventListener {

	default void onPatreonPledge(PatreonPledgeEvent event) {}
	
	default void onPatreonPledgeCreate(PatreonPledgeCreateEvent event) {}
	
	default void onPatreonPledgeUpdate(PatreonPledgeUpdateEvent event) {}
	
	default void onPatreonPledgeDelete(PatreonPledgeDeleteEvent event) {}
	
}
