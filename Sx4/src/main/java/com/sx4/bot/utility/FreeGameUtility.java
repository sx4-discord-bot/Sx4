package com.sx4.bot.utility;

import com.sx4.bot.entities.info.game.EpicFreeGame;
import com.sx4.bot.entities.info.game.FreeGame;
import com.sx4.bot.entities.info.game.FreeGameType;
import com.sx4.bot.http.HttpCallback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.bson.Document;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class FreeGameUtility {

	public static final Request REQUEST = new Request.Builder()
		.url("https://store-site-backend-static-ipv4.ak.epicgames.com/freeGamesPromotions?locale=en-US&country=GB&allowCountries=GB")
		.build();

	public static Document getPromotionalOffer(Document data, Function<Document, List<Document>> function) {
		Document promotions = data.get("promotions", Document.class);
		if (promotions == null) {
			return null;
		}

		List<Document> offers = function.apply(promotions);
		if (offers == null || offers.isEmpty()) {
			return null;
		}

		offers = offers.get(0).getList("promotionalOffers", Document.class, Collections.emptyList());
		if (offers.isEmpty()) {
			return null;
		}

		return offers.get(0);
	}

	public static Document getBestPromotionalOffer(Document data) {
		return FreeGameUtility.getPromotionalOffer(data, promotions -> {
			return promotions.keySet().stream()
				.map(key -> promotions.getList(key, Document.class))
				.min(Comparator.comparingInt(offers -> {
					if (offers.isEmpty()) {
						return 100;
					}

					offers = offers.get(0).getList("promotionalOffers", Document.class, Collections.emptyList());
					if (offers.isEmpty()) {
						return 100;
					}

					return offers.get(0).getEmbedded(List.of("discountSetting", "discountPercentage"), Integer.class);
				}))
				.orElse(Collections.emptyList());
		});
	}

	public static Document getCurrentPromotionalOffer(Document data) {
		return FreeGameUtility.getPromotionalOffer(data, promotions -> promotions.getList("promotionalOffers", Document.class));
	}

	public static Document getUpcomingPromotionalOffer(Document data) {
		return FreeGameUtility.getPromotionalOffer(data, promotions -> promotions.getList("upcomingPromotionalOffers", Document.class));
	}

	public static void retrieveFreeGames(OkHttpClient client, Predicate<Document> predicate, Consumer<List<EpicFreeGame>> consumer) {
		client.newCall(FreeGameUtility.REQUEST).enqueue((HttpCallback) response -> {
			Document document = Document.parse(response.body().string());

			List<Document> elements = document.getEmbedded(List.of("data", "Catalog", "searchStore", "elements"), Collections.emptyList());

			List<EpicFreeGame> freeGames = elements.stream()
				.filter(predicate)
				.map(EpicFreeGame::fromData)
				.collect(Collectors.toList());

			consumer.accept(freeGames);
		});
	}

	private static boolean getValidFreeGames(Document game, Function<Document, Document> offerFunction) {
		Document price = game.getEmbedded(List.of("price", "totalPrice"), Document.class);

		Document offer = offerFunction.apply(game);
		return (offer != null && offer.getEmbedded(List.of("discountSetting", "discountPercentage"), Integer.class) == 0);
	}

	public static void retrieveFreeGames(OkHttpClient client, Consumer<List<EpicFreeGame>> consumer) {
		FreeGameUtility.retrieveFreeGames(client, game -> FreeGameUtility.getValidFreeGames(game, FreeGameUtility::getBestPromotionalOffer), consumer);
	}

	public static void retrieveCurrentFreeGames(OkHttpClient client, Consumer<List<EpicFreeGame>> consumer) {
		FreeGameUtility.retrieveFreeGames(client, game -> FreeGameUtility.getValidFreeGames(game, FreeGameUtility::getCurrentPromotionalOffer), consumer);
	}

	public static void retrieveUpcomingFreeGames(OkHttpClient client, Consumer<List<EpicFreeGame>> consumer) {
		FreeGameUtility.retrieveFreeGames(client, game -> FreeGameUtility.getValidFreeGames(game, FreeGameUtility::getUpcomingPromotionalOffer), consumer);
	}

	public static FreeGame<?> getFreeGame(Document data) {
		FreeGameType type = FreeGameType.fromId(data.getInteger("type"));
		return type.fromDatabase(data);
	}

}
