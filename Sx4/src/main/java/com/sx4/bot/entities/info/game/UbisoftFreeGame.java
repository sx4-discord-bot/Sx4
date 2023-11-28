package com.sx4.bot.entities.info.game;

import org.bson.Document;
import org.jsoup.nodes.Element;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.Locale;

public class UbisoftFreeGame extends FreeGame<String> {

	private static final DateTimeFormatterBuilder FORMATTER = new DateTimeFormatterBuilder().appendPattern("dd/MM/uuuu [hh][h]:mma");
	private static final ZoneId ZONE_ID = ZoneId.of("Europe/London");

	public UbisoftFreeGame(String id, String title, String description, String publisher, String image, int originalPrice, int discountPrice, OffsetDateTime start, OffsetDateTime end) {
		super(id, title, description, publisher, image, originalPrice, discountPrice, start, end, FreeGameType.UBISOFT);
	}

	@Override
	public String getUrl() {
		return "https://store.ubisoft.com/" + this.getId() + ".html";
	}

	@Override
	public String getRunUrl() {
		return null;
	}

	@Override
	public boolean isDLC() {
		return false;
	}

	public static UbisoftFreeGame fromData(String id, Element content) {
		String title = content.getElementsByClass("c-pdp-banner__product-name").text();

		Element offer = content.getElementsByClass("offer-selector-price").first();
		int originalPrice = (int) (Double.parseDouble(offer.getElementsByClass("price-item").text().substring(1)) * 100);

		String promotionEnding = offer.getElementsByClass("offer-info").first().text();
		String time = promotionEnding.substring(promotionEnding.indexOf(" on ") + 4);
		time = time.substring(0, time.length() - 2) + time.substring(time.length() - 2).toUpperCase(Locale.ROOT);

		DateTimeFormatter formatter = UbisoftFreeGame.FORMATTER.toFormatter(Locale.ROOT).withZone(UbisoftFreeGame.ZONE_ID);
		OffsetDateTime end = ZonedDateTime.parse(time, formatter).withZoneSameInstant(ZoneOffset.UTC).toOffsetDateTime();

		Element descriptionElement = content.getElementsByClass("c-pdp-general-info__group")
			.stream()
			.filter(element -> element.getElementsByTag("dt").text().equals("Description:"))
			.findFirst()
			.orElse(null);

		StringBuilder description = new StringBuilder();
		if (descriptionElement == null) {
			description.append("No description.");
		} else {
			Element textElement = descriptionElement.getElementsByTag("dd").first();
			description.append(textElement.ownText());
			description.append(textElement.getElementsByTag("div").text());
		}

		String legalText = content.getElementsByClass("c-pdp-general-info__legal-line").text();
		String publisher = legalText.substring(legalText.indexOf("©") + 7, legalText.indexOf("."));

		String image = content.getElementsByClass("c-pdp-banner__cover-overlay restriction-area")
			.first()
			.getElementsByTag("source")
			.first()
			.attr("srcset");

		return new UbisoftFreeGame(id, title, description.toString(), publisher, image, originalPrice, 0, OffsetDateTime.now(), end);
	}

	public static UbisoftFreeGame fromDatabase(Document data) {
		String id = data.getString("gameId");
		String title = data.getString("title");
		String description = data.getString("description");
		String publisher = data.getString("publisher");
		String image = data.getString("image");

		Document promotion = data.get("promotion", Document.class);
		OffsetDateTime start = OffsetDateTime.ofInstant(Instant.ofEpochSecond(promotion.getLong("start")), ZoneOffset.UTC);
		OffsetDateTime end = OffsetDateTime.ofInstant(Instant.ofEpochSecond(promotion.getLong("end")), ZoneOffset.UTC);

		Document priceInfo = data.get("price", Document.class);
		int originalPrice = priceInfo.getInteger("original");
		int discountPrice = priceInfo.getInteger("discount");

		return new UbisoftFreeGame(id, title, description, publisher, image, originalPrice, discountPrice, start, end);
	}

}
