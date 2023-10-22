package com.sx4.bot.handlers;


import com.mongodb.client.model.Updates;
import com.sx4.bot.core.Sx4;
import com.sx4.bot.database.mongo.MongoDatabase;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.RawGatewayEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.utils.data.DataObject;
import org.jetbrains.annotations.NotNull;

import java.time.OffsetDateTime;

public class EntitlementsHandler implements EventListener {

	private final Sx4 bot;

	public EntitlementsHandler(Sx4 bot) {
		this.bot = bot;
	}

	@Override
	public void onEvent(@NotNull GenericEvent genericEvent) {
		if (!(genericEvent instanceof RawGatewayEvent event)) {
			return;
		}

		String type = event.getType();
		if (!type.equals("ENTITLEMENT_CREATE") && !type.equals("ENTITLEMENT_UPDATE")) {
			return;
		}

		DataObject data = event.getPayload();
		if (!data.hasKey("guild_id")) {
			return;
		}

		long guildId = data.getLong("guild_id");
		OffsetDateTime ends = data.getOffsetDateTime("ends_at");

		this.bot.getMongo().updateGuildById(guildId, Updates.set("premium.endAt", ends.toEpochSecond())).whenComplete(MongoDatabase.exceptionally());
	}

}
