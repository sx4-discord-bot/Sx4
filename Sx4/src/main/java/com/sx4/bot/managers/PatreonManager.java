package com.sx4.bot.managers;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.WriteModel;
import com.sx4.bot.core.Sx4;
import com.sx4.bot.database.mongo.MongoDatabase;
import com.sx4.bot.database.mongo.model.Operators;
import com.sx4.bot.events.patreon.PatreonEvent;
import com.sx4.bot.hooks.PatreonListener;
import com.sx4.bot.http.HttpCallback;
import com.sx4.bot.utility.ExceptionUtility;
import com.sx4.bot.utility.FutureUtility;
import okhttp3.Request;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class PatreonManager {

	private final List<PatreonListener> listeners;
	private final Sx4 bot;
	
	public PatreonManager(Sx4 bot) {
		this.bot = bot;
		this.listeners = new ArrayList<>();
	}
	
	public PatreonManager addListener(PatreonListener listener) {
		this.listeners.add(listener);
		
		return this;
	}
	
	public PatreonManager removeListener(PatreonListener listener) {
		this.listeners.remove(listener);
		
		return this;
	}
	
	public void onPatreonEvent(PatreonEvent event) {
		for (PatreonListener listener : this.listeners) {
			listener.onPatreonEvent(event);
		}
	}

	public void ensurePatrons(String cursor, List<CompletableFuture<List<WriteModel<Document>>>> futures) {
		Request request = new Request.Builder()
			.url("https://www.patreon.com/api/oauth2/v2/campaigns/" + this.bot.getConfig().getPatreonCampaignId() + "/members?fields%5Bmember%5D=lifetime_support_cents&fields%5Buser%5D=social_connections&include=user" + (cursor == null ? "" : "&page%5Bcursor%5D=" + cursor))
			.header("Authorization", "Bearer " + this.bot.getConfig().getPatreonAccessToken())
			.build();

		CompletableFuture<List<WriteModel<Document>>> future = new CompletableFuture<>();
		futures.add(future);

		this.bot.getHttpClient().newCall(request).enqueue((HttpCallback) response -> {
			Document data = Document.parse(response.body().string());

			List<WriteModel<Document>> bulkData = new ArrayList<>();

			Map<String, Integer> total = new HashMap<>();
			for (Document member : data.getList("data", Document.class)) {
				int totalAmount = member.getEmbedded(List.of("attributes", "lifetime_support_cents"), 0);
				if (totalAmount != 0) {
					total.put(member.getEmbedded(List.of("relationships", "user", "data", "id"), String.class), totalAmount);
				}
			}

			for (Document user : data.getList("included", Document.class)) {
				String discordId = user.getEmbedded(List.of("attributes", "social_connections", "discord", "user_id"), String.class);
				if (discordId == null) {
					continue;
				}

				int totalAmount = total.getOrDefault(user.getString("id"), 0);
				if (totalAmount == 0) {
					continue;
				}

				List<Bson> update = List.of(
					Operators.set("premium.credit", Operators.let(new Document("credit", Operators.ifNull("$premium.credit", 0)).append("total", Operators.ifNull("$premium.total", 0)), Operators.add("$$credit", Operators.subtract(totalAmount, "$$total")))),
					Operators.set("premium.total", totalAmount)
				);

				bulkData.add(new UpdateOneModel<>(Filters.eq("_id", Long.parseLong(discordId)), update, new UpdateOptions().upsert(true)));
			}

			future.complete(bulkData);

			String nextCursor = data.getEmbedded(List.of("meta", "pagination", "cursors", "next"), String.class);
			if (nextCursor == null) {
				FutureUtility.allOf(futures).whenComplete((result, exception) -> {
					if (ExceptionUtility.sendErrorMessage(this.bot.getShardManager(), exception)) {
						return;
					}

					List<WriteModel<Document>> allBulkData = result.stream().flatMap(List::stream).collect(Collectors.toList());
					if (!allBulkData.isEmpty()) {
						this.bot.getMongoMain().bulkWriteUsers(allBulkData).whenComplete(MongoDatabase.exceptionally(this.bot.getShardManager()));
					}
				});
			} else {
				this.ensurePatrons(nextCursor, futures);
			}
		});
	}
	
}
