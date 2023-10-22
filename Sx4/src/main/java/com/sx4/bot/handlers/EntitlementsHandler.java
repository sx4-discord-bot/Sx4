package com.sx4.bot.handlers;


import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.WriteModel;
import com.sx4.bot.core.Sx4;
import com.sx4.bot.database.mongo.MongoDatabase;
import com.sx4.bot.http.HttpCallback;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.RawGatewayEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.requests.RestConfig;
import net.dv8tion.jda.api.utils.data.DataArray;
import net.dv8tion.jda.api.utils.data.DataObject;
import okhttp3.Request;
import org.bson.Document;
import org.jetbrains.annotations.NotNull;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

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

	public void ensureEntitlements() {
		long applicationId = this.bot.getShardManager().getShards().get(0).getSelfUser().getIdLong();

		Request request = new Request.Builder()
			.url(RestConfig.DEFAULT_BASE_URL + "applications/" + applicationId + "/entitlements")
			.header("Authorization", "Bot " + this.bot.getConfig().getToken())
			.build();

		this.bot.getHttpClient().newCall(request).enqueue((HttpCallback) response -> {
			DataArray array = DataArray.fromJson(response.body().string());

			List<WriteModel<Document>> bulkData = new ArrayList<>();
			for (int i = 0; i < array.length(); i++) {
				DataObject data = array.getObject(i);
				if (!data.hasKey("guild_id") && !data.hasKey("ends_at")) {
					continue;
				}

				long guildId = data.getLong("guild_id");
				OffsetDateTime ends = data.getOffsetDateTime("ends_at");

				bulkData.add(new UpdateOneModel<>(Filters.eq("guildId", guildId), Updates.set("premium.endAt", ends.toEpochSecond())));
			}

			if (!bulkData.isEmpty()) {
				this.bot.getMongo().bulkWriteGuilds(bulkData).whenComplete(MongoDatabase.exceptionally());
			}
		});
	}

}
