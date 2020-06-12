package com.sx4.bot.handlers;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.bson.Document;
import org.bson.conversions.Bson;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.WriteModel;
import com.sx4.bot.database.Database;
import com.sx4.bot.events.patreon.PatreonPledgeCreateEvent;
import com.sx4.bot.events.patreon.PatreonPledgeDeleteEvent;
import com.sx4.bot.events.patreon.PatreonPledgeUpdateEvent;
import com.sx4.bot.hooks.PatreonListener;
import com.sx4.bot.utility.ExceptionUtility;

public class PatreonHandler implements PatreonListener {

	public void onPatreonPledgeCreate(PatreonPledgeCreateEvent event) {
		if (!event.hasDiscord()) {
			return;
		}
		
		Bson update = Updates.combine(Updates.set("patreon.amount", event.getAmount()), Updates.set("patreon.since", Clock.systemUTC().instant().getEpochSecond()));
		Database.get().updateUserById(event.getDiscordId(), update).whenComplete((result, exception) -> {
			if (exception != null) {
				ExceptionUtility.sendErrorMessage(exception);
			}
		});
	}
	
	public void onPatreonPledgeUpdate(PatreonPledgeUpdateEvent event) {
		if (!event.hasDiscord()) {
			return;
		}
		
		Database.get().updateUserById(event.getDiscordId(), Updates.set("patreon.amount", event.getAmount())).whenComplete((result, exception) -> {
			if (exception != null) {
				ExceptionUtility.sendErrorMessage(exception);
			}
		});
	}
	
	public void onPatreonPledgeDelete(PatreonPledgeDeleteEvent event) {
		if (!event.hasDiscord()) {
			return;
		}
		
		Database database = Database.get();
		
		FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().returnDocument(ReturnDocument.BEFORE).projection(Projections.include("patreon.guilds"));
		database.findAndUpdateUserById(event.getDiscordId(), Updates.unset("patreon"), options).whenComplete((data, exception) -> {
			if (exception != null) {
				ExceptionUtility.sendErrorMessage(exception);
				return;
			}
			
			List<Long> guilds = data.getEmbedded(List.of("patreon", "guilds"), Collections.emptyList());
			
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
	
}
