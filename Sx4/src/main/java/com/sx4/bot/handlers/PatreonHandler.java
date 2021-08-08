package com.sx4.bot.handlers;

import com.sx4.bot.core.Sx4;
import com.sx4.bot.database.mongo.MongoDatabase;
import com.sx4.bot.database.mongo.model.Operators;
import com.sx4.bot.events.patreon.PatreonEvent;
import com.sx4.bot.hooks.PatreonListener;
import org.bson.conversions.Bson;

import java.util.List;

public class PatreonHandler implements PatreonListener {

	private final Sx4 bot;

	public PatreonHandler(Sx4 bot) {
		this.bot = bot;
	}

	public void onPatreonEvent(PatreonEvent event) {
		List<Bson> update = List.of(
			Operators.set("premium.credit", Operators.add(Operators.ifNull("$premium.credit", 0), Operators.subtract(event.getTotalAmount(), Operators.ifNull("$premium.total", 0)))),
			Operators.set("premium.endAt", Operators.add(Operators.cond(Operators.or(Operators.extinct("$premium.endAt"), Operators.lt("$premium.endAt", Operators.nowEpochSecond())), Operators.nowEpochSecond(), "$premium.endAt"), Operators.multiply(Operators.toInt(Operators.round(Operators.multiply(Operators.divide(Operators.subtract(event.getTotalAmount(), Operators.ifNull("$premium.total", 0)), this.bot.getConfig().getPremiumPrice()), this.bot.getConfig().getPremiumDays()))), 86400))),
			Operators.set("premium.total", event.getTotalAmount())
		);

		this.bot.getMongoMain().updateUserById(event.getDiscordId(), update).whenComplete(MongoDatabase.exceptionally(this.bot.getShardManager()));
	}
	
}
