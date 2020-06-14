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

import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.api.hooks.EventListener;

public class PatreonHandler implements PatreonListener, EventListener {
	
	public static final PatreonHandler INSTANCE = new PatreonHandler();

	public void onPatreonPledgeCreate(PatreonPledgeCreateEvent event) {
		if (event.getAmount() == 0) {
			return;
		}
		
		Bson update = Updates.combine(
			Updates.set("amount", event.getAmount()),
			Updates.set("since", Clock.systemUTC().instant().getEpochSecond()),
			Updates.set("guilds", List.of())
		);
		
		if (event.hasDiscord()) {
			update = Updates.combine(update, Updates.set("discordId", event.getDiscordId()));
		}
		
		Database.get().updatePatronById(event.getId(), update).whenComplete((result, exception) -> ExceptionUtility.sendErrorMessage(exception));
	}
	
	public void onPatreonPledgeUpdate(PatreonPledgeUpdateEvent event) {
		Database.get().updatePatronById(event.getId(), Updates.set("amount", event.getAmount())).whenComplete((result, exception) -> ExceptionUtility.sendErrorMessage(exception));
	}
	
	public void onPatreonPledgeDelete(PatreonPledgeDeleteEvent event) {
		Database database = Database.get();
		
		database.findAndDeletePatronById(event.getId(), Projections.include("guilds")).whenComplete((data, exception) -> {
			if (ExceptionUtility.sendErrorMessage(exception)) {
				return;
			}
			
			List<Long> guilds = data.getList("guilds", Long.class, Collections.emptyList());
			
			List<WriteModel<Document>> bulkData = new ArrayList<>();
			for (long guildId : guilds) {
				bulkData.add(new UpdateOneModel<>(Filters.eq("_id", guildId), Updates.unset("premium")));
			}
			
			if (!bulkData.isEmpty()) {
    			database.bulkWriteGuilds(bulkData).whenComplete((result, guildException) -> ExceptionUtility.sendErrorMessage(guildException));
			}
		});
	}
	
	public void onEvent(GenericEvent event) {
		if (event instanceof GuildLeaveEvent) {
			long guildId = ((GuildLeaveEvent) event).getGuild().getIdLong();
			
			Database database = Database.get();
			
			FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().returnDocument(ReturnDocument.BEFORE).projection(Projections.include("premium"));
			database.findAndUpdateGuildById(guildId, Updates.unset("premium"), options).whenComplete((data, exception) -> {
				if (ExceptionUtility.sendErrorMessage(exception)) {
					return;
				}
				
				if (data == null) {
					return;
				}
				
				database.updatePatronByFilter(Filters.eq("discordId", data.get("premium", 0L)), Updates.pull("guilds", guildId)).whenComplete((result, patronException) -> ExceptionUtility.sendErrorMessage(patronException));
			});
		}
	}
	
}
