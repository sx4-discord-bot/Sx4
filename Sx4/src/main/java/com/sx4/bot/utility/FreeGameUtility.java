package com.sx4.bot.utility;

import com.sx4.bot.entities.info.FreeGame;
import com.sx4.bot.http.HttpCallback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.bson.Document;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class FreeGameUtility {

	public static Request REQUEST = new Request.Builder()
		.url("https://store-site-backend-static-ipv4.ak.epicgames.com/freeGamesPromotions?locale=en-US&country=GB&allowCountries=GB")
		.build();

	public static Document getPromotionalOffer(Document data, Function<Document, List<Document>> function) {
		Document promotions = data.get("promotions", Document.class);
		if (promotions == null) {
			return null;
		}

		List<Document> offers = function.apply(promotions);
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
		return FreeGameUtility.getPromotionalOffer(data, promotions -> {
			List<Document> offers = promotions.getList("upcomingPromotionalOffers", Document.class);
			if (offers.isEmpty()) {
				offers = promotions.getList("promotionalOffers", Document.class);
			}

			return offers;
		});
	}

	public static Document getCurrentPromotionalOffer(Document data) {
		return FreeGameUtility.getPromotionalOffer(data, promotions -> promotions.getList("promotionalOffers", Document.class));
	}

	public static Document getUpcomingPromotionalOffer(Document data) {
		return FreeGameUtility.getPromotionalOffer(data, promotions -> promotions.getList("upcomingPromotionalOffers", Document.class));
	}

	public static void retrieveFreeGames(OkHttpClient client, Predicate<Document> predicate, Consumer<List<FreeGame>> consumer) {
		client.newCall(FreeGameUtility.REQUEST).enqueue((HttpCallback) response -> {
			Document document = Document.parse(response.body().string());

			List<Document> elements = document.getEmbedded(List.of("data", "Catalog", "searchStore", "elements"), Collections.emptyList());

			List<FreeGame> freeGames = elements.stream()
				.filter(predicate)
				.map(FreeGame::fromData)
				.collect(Collectors.toList());

			consumer.accept(freeGames);
		});
	}

	public static void retrieveFreeGames(OkHttpClient client, Consumer<List<FreeGame>> consumer) {
		FreeGameUtility.retrieveFreeGames(client, game -> {
			Document offer = FreeGameUtility.getPromotionalOffer(game);
			return offer != null && offer.getEmbedded(List.of("discountSetting", "discountPercentage"), Integer.class) == 0;
		}, consumer);
	}

	public static void retrieveCurrentFreeGames(OkHttpClient client, Consumer<List<FreeGame>> consumer) {
		FreeGameUtility.retrieveFreeGames(client, game -> {
			Document offer = FreeGameUtility.getCurrentPromotionalOffer(game);
			return offer != null && offer.getEmbedded(List.of("discountSetting", "discountPercentage"), Integer.class) == 0;
		}, consumer);
	}

	public static void retrieveUpcomingFreeGames(OkHttpClient client, Consumer<List<FreeGame>> consumer) {
		FreeGameUtility.retrieveFreeGames(client, game -> {
			Document offer = FreeGameUtility.getUpcomingPromotionalOffer(game);
			return offer != null && offer.getEmbedded(List.of("discountSetting", "discountPercentage"), Integer.class) == 0;
		}, consumer);
	}

}
