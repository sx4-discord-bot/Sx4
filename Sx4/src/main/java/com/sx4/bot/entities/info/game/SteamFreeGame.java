package com.sx4.bot.entities.info.game;

import org.bson.Document;
import org.jsoup.nodes.Element;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.Locale;

public class SteamFreeGame extends FreeGame<Integer> {

	private static final DateTimeFormatterBuilder FORMATTER = new DateTimeFormatterBuilder().appendPattern("[dd][d] MMM @ [hh][h]:mma");
	private static final ZoneId ZONE_ID = ZoneId.of("America/Los_Angeles");

	private final boolean dlc;

	private SteamFreeGame(int id, String title, String description, String publisher, String image, int originalPrice, int discountPrice, boolean dlc, OffsetDateTime start, OffsetDateTime end) {
		super(id, title, description, publisher, image, originalPrice, discountPrice, start, end, FreeGameType.STEAM);

		this.dlc = dlc;
	}

	public boolean isDLC() {
		return this.dlc;
	}

	public String getUrl() {
		return "https://store.steampowered.com/app/" + this.getId();
	}

	public String getRunUrl() {
		return "<steam://store/" + this.getId() + ">";
	}

	public static SteamFreeGame fromData(int id, Element element) {
		String title = element.getElementById("appHubAppName").text();

		Element info = element.getElementsByClass("glance_ctn").first();
		String description = info.getElementsByClass("game_description_snippet").text();
		String image = info.getElementsByClass("game_header_image_full").attr("src");

		boolean dlc = element.getElementsByClass("game_area_bubble game_area_dlc_bubble ").first() != null;

		String publisher = info.getElementsByClass("dev_row").stream()
			.filter(e -> e.getElementsByClass("subtitle column").first().text().equals("Publisher:"))
			.flatMap(e -> e.getElementsByClass("summary column").first().children().stream())
			.map(Element::text)
			.findFirst()
			.orElse(null);

		Element prices = element.getElementsByClass("discount_prices").first();
		if (prices == null) {
			return null;
		}

		String originalPriceFormatted = prices.getElementsByClass("discount_original_price").first().text();
		String discountPriceFormatted = prices.getElementsByClass("discount_final_price").first().text();

		int originalPrice = (int) (Double.parseDouble(originalPriceFormatted.substring(1)) * 100);
		int discountPrice = (int) (Double.parseDouble(discountPriceFormatted.substring(1)) * 100);

		String endText = element.getElementsByClass("game_purchase_discount_quantity ").first().text();

		int beforeIndex = endText.indexOf("before ");
		if (beforeIndex == -1) {
			return null;
		}

		int startIndex = beforeIndex + 7, endIndex = endText.indexOf(".");

		DateTimeFormatter formatter = SteamFreeGame.FORMATTER
			.parseDefaulting(ChronoField.YEAR, Year.now(SteamFreeGame.ZONE_ID).getValue())
			.toFormatter(Locale.ROOT)
			.withZone(SteamFreeGame.ZONE_ID);

		OffsetDateTime end = ZonedDateTime.parse(endText.substring(startIndex, endIndex - 2) + endText.substring(endIndex - 2, endIndex).toUpperCase(Locale.ROOT), formatter).withZoneSameInstant(ZoneOffset.UTC).toOffsetDateTime();
		if (end.isBefore(OffsetDateTime.now(ZoneOffset.UTC))) {
			end = end.plusYears(1);
		}

		return new SteamFreeGame(id, title, description, publisher, image, originalPrice, discountPrice, dlc, OffsetDateTime.now(), end);
	}

	public static SteamFreeGame fromDatabase(Document data) {
		int id = data.getInteger("gameId");
		String title = data.getString("title");
		String description = data.getString("description");
		String publisher = data.getString("publisher");
		String image = data.getString("image");
		boolean dlc = data.getBoolean("dlc", false);

		Document promotion = data.get("promotion", Document.class);
		OffsetDateTime start = OffsetDateTime.ofInstant(Instant.ofEpochSecond(promotion.getLong("start")), ZoneOffset.UTC);
		OffsetDateTime end = OffsetDateTime.ofInstant(Instant.ofEpochSecond(promotion.getLong("end")), ZoneOffset.UTC);

		Document priceInfo = data.get("price", Document.class);
		int originalPrice = priceInfo.getInteger("original");
		int discountPrice = priceInfo.getInteger("discount");

		return new SteamFreeGame(id, title, description, publisher, image, originalPrice, discountPrice, dlc, start, end);
	}

	public Document toData() {
		return super.toData().append("dlc", this.dlc);
	}

}
