package com.sx4.bot.handlers;

import com.sx4.bot.core.Sx4;
import com.sx4.bot.database.mongo.MongoDatabase;
import com.sx4.bot.database.mongo.model.Operators;
import com.sx4.bot.events.patreon.PatreonEvent;
import com.sx4.bot.hooks.PatreonListener;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.List;

public class PatreonHandler implements PatreonListener {

	private final Sx4 bot;

	public PatreonHandler(Sx4 bot) {
		this.bot = bot;
	}

	public void onPatreonEvent(PatreonEvent event) {
		List<Bson> update = List.of(
			Operators.set("premium.credit", Operators.let(new Document("credit", Operators.ifNull("$premium.credit", 0)).append("total", Operators.ifNull("$premium.total", 0)), Operators.add("$$credit", Operators.subtract(event.getTotalAmount(), "$$total")))),
			Operators.set("premium.total", event.getTotalAmount())
		);

		this.bot.getMongoMain().updateUserById(event.getDiscordId(), update).whenComplete(MongoDatabase.exceptionally(this.bot.getShardManager()));
	}
	
}
