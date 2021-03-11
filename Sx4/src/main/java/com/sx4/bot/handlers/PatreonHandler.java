package com.sx4.bot.handlers;

import com.mongodb.client.model.Updates;
import com.sx4.bot.core.Sx4;
import com.sx4.bot.database.Database;
import com.sx4.bot.events.patreon.PatreonPledgeCreateEvent;
import com.sx4.bot.events.patreon.PatreonPledgeUpdateEvent;
import com.sx4.bot.hooks.PatreonListener;

public class PatreonHandler implements PatreonListener {

	private final Sx4 bot;

	public PatreonHandler(Sx4 bot) {
		this.bot = bot;
	}

	private void updateCredit(long discordId, int amount) {
		if (amount == 0 || discordId == 0L) {
			return;
		}

		this.bot.getDatabase().updateUserById(discordId, Updates.inc("premium.credit", amount)).whenComplete(Database.exceptionally(this.bot.getShardManager()));
	}

	public void onPatreonPledgeCreate(PatreonPledgeCreateEvent event) {
		this.updateCredit(event.getDiscordId(), event.getAmount());
	}
	
	public void onPatreonPledgeUpdate(PatreonPledgeUpdateEvent event) {
		this.updateCredit(event.getDiscordId(), event.getAmount());
	}
	
}
