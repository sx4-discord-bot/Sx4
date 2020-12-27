package com.sx4.bot.handlers;

import com.mongodb.client.model.Updates;
import com.sx4.bot.database.Database;
import com.sx4.bot.events.patreon.PatreonPledgeCreateEvent;
import com.sx4.bot.events.patreon.PatreonPledgeUpdateEvent;
import com.sx4.bot.hooks.PatreonListener;

public class PatreonHandler implements PatreonListener {
	
	public static final PatreonHandler INSTANCE = new PatreonHandler();

	private void updateCredit(long discordId, int amount) {
		if (amount == 0 || discordId == 0L) {
			return;
		}

		Database.get().updateUserById(discordId, Updates.inc("premium.credit", amount)).whenComplete(Database.exceptionally());
	}

	public void onPatreonPledgeCreate(PatreonPledgeCreateEvent event) {
		this.updateCredit(event.getDiscordId(), event.getAmount());
	}
	
	public void onPatreonPledgeUpdate(PatreonPledgeUpdateEvent event) {
		this.updateCredit(event.getDiscordId(), event.getAmount());
	}
	
}
