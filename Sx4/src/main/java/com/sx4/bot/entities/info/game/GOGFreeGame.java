package com.sx4.bot.entities.info.game;

import org.bson.Document;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public class GOGFreeGame extends FreeGame<Integer> {

	private final String url;

	private GOGFreeGame(int id, String title, String description, String publisher, String image, String url, int originalPrice, int discountPrice, OffsetDateTime start, OffsetDateTime end) {
		super(id, title, description, publisher, image, originalPrice, discountPrice, start, end, FreeGameType.GOG);

		this.url = url;
	}

	public boolean isDLC() {
		return false;
	}

	public String getUrl() {
		return "https://gog.com" + this.url;
	}

	public String getRunUrl() {
		return null;
	}

	public Document toData() {
		return super.toData().append("url", this.url);
	}

	public static GOGFreeGame fromData(int id, String title, String description, String publisher, String url, int originalPrice, OffsetDateTime end, String image) {
		return new GOGFreeGame(id, title, description, publisher, image, url, originalPrice, 0, OffsetDateTime.now(), end);
	}

	public static GOGFreeGame fromDatabase(Document data) {
		int id = data.getInteger("gameId");
		String title = data.getString("title");
		String description = data.getString("description");
		String publisher = data.getString("publisher");
		String url = data.getString("url");
		String image = data.getString("image");

		Document promotion = data.get("promotion", Document.class);
		OffsetDateTime start = OffsetDateTime.ofInstant(Instant.ofEpochSecond(promotion.getLong("start")), ZoneOffset.UTC);
		OffsetDateTime end = OffsetDateTime.ofInstant(Instant.ofEpochSecond(promotion.getLong("end")), ZoneOffset.UTC);

		Document priceInfo = data.get("price", Document.class);
		int originalPrice = priceInfo.getInteger("original");
		int discountPrice = priceInfo.getInteger("discount");

		return new GOGFreeGame(id, title, description, publisher, image, url, originalPrice, discountPrice, start, end);
	}

}
