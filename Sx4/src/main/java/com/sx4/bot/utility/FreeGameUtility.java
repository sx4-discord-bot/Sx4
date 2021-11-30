package com.sx4.bot.utility;

import okhttp3.Request;
import org.bson.Document;

import java.util.Collections;
import java.util.List;

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

}
