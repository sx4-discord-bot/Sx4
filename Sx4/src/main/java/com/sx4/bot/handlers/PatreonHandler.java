package com.sx4.bot.handlers;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.bson.Document;
import org.bson.conversions.Bson;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.WriteModel;
import com.sx4.bot.database.Database;
import com.sx4.bot.events.patreon.PatreonMemberUpdateEvent;
import com.sx4.bot.events.patreon.PatreonPledgeCreateEvent;
import com.sx4.bot.events.patreon.PatreonPledgeDeleteEvent;
import com.sx4.bot.events.patreon.PatreonPledgeUpdateEvent;
import com.sx4.bot.hooks.PatreonListener;
import com.sx4.bot.utility.ExceptionUtility;

public class PatreonHandler implements PatreonListener {

	public void onPatreonPledgeCreate(PatreonPledgeCreateEvent event) {
		Bson update = Updates.combine(
			Updates.set("amount", event.getAmount()),
			Updates.set("since", Clock.systemUTC().instant().getEpochSecond())
		);
		
		if (event.hasDiscord()) {
			update = Updates.combine(update, Updates.set("discordId", event.getDiscordId()));
		}
		
		Database.get().updatePatronById(event.getId(), update).whenComplete((result, exception) -> {
			if (exception != null) {
				ExceptionUtility.sendErrorMessage(exception);
			}
		});
	}
	
	public void onPatreonPledgeUpdate(PatreonPledgeUpdateEvent event) {
		Database.get().updatePatronById(event.getId(), Updates.set("amount", event.getAmount())).whenComplete((result, exception) -> {
			if (exception != null) {
				ExceptionUtility.sendErrorMessage(exception);
			}
		});
	}
	
	public void onPatreonPledgeDelete(PatreonPledgeDeleteEvent event) {
		Database database = Database.get();
		
		database.findAndDeletePatronById(event.getId(), Projections.include("guilds")).whenComplete((data, exception) -> {
			if (exception != null) {
				ExceptionUtility.sendErrorMessage(exception);
				return;
			}
			
			List<Long> guilds = data.getList("guilds", Long.class, Collections.emptyList());
			
			List<WriteModel<Document>> bulkData = new ArrayList<>();
			for (long guildId : guilds) {
				bulkData.add(new UpdateOneModel<>(Filters.eq("_id", guildId), Updates.unset("premium")));
			}
			
			if (!bulkData.isEmpty()) {
    			database.bulkWriteGuilds(bulkData).whenComplete((result, guildException) -> {
    				if (guildException != null) {
        				ExceptionUtility.sendErrorMessage(guildException);
        			}
    			});
			}
		});
	}
	
	public void onPatreonMemberUpdate(PatreonMemberUpdateEvent event) {
		if (!event.hasDiscord()) {
			return;
		}
		
		Database.get().updatePatronById(event.getId(), Updates.set("discordId", event.getDiscordId())).whenComplete((result, exception) -> {
			if (exception != null) {
				ExceptionUtility.sendErrorMessage(exception);
			}
		});
	}
	
}
