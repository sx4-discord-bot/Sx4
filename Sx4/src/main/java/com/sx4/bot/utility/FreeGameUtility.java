package com.sx4.bot.utility;

import com.sx4.bot.entities.info.FreeGame;
import com.sx4.bot.http.HttpCallback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.bson.Document;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class FreeGameUtility {

	public static Request REQUEST = new Request.Builder()
		.url("https://store-site-backend-static-ipv4.ak.epicgames.com/freeGamesPromotions?locale=en-US&country=GB&allowCountries=GB")
		.build();

	public static Document getPromotionalOffer(Document data, String key) {
		Document promotions = data.get("promotions", Document.class);
		if (promotions == null) {
			return null;
		}

		List<Document> offers = promotions.getList(key, Document.class, Collections.emptyList());
		if (offers.isEmpty()) {
			return null;
		}

		offers = offers.get(0).getList("promotionalOffers", Document.class, Collections.emptyList());
		if (offers.isEmpty()) {
			return null;
		}

		return offers.get(0);
	}

	public static Document getPromotionalOffer(Document data) {
		return FreeGameUtility.getPromotionalOffer(data, "promotionalOffers");
	}

	public static Document getUpcomingPromotionalOffer(Document data) {
		return FreeGameUtility.getPromotionalOffer(data, "upcomingPromotionalOffers");
	}

	public static void retrieveFreeGames(OkHttpClient client, Consumer<List<FreeGame>> consumer) {
		client.newCall(FreeGameUtility.REQUEST).enqueue((HttpCallback) response -> {
			Document document = Document.parse(response.body().string());

			List<Document> elements = document.getEmbedded(List.of("data", "Catalog", "searchStore", "elements"), Collections.emptyList());

			List<FreeGame> freeGames = elements.stream()
				.filter(game -> {
					Document offer = FreeGameUtility.getPromotionalOffer(game);
					return offer != null && offer.getEmbedded(List.of("discountSetting", "discountPercentage"), Integer.class) == 0;
				})
				.map(game -> FreeGame.fromData(game, true))
				.collect(Collectors.toList());

			consumer.accept(freeGames);
		});
	}

}
