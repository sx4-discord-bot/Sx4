package com.sx4.bot.entities.info.game;

import com.sx4.bot.utility.FreeGameUtility;
import org.bson.Document;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;

public class EpicFreeGame extends FreeGame<String> {

	private final String url;
	private final String offerType;

	private EpicFreeGame(String id, String title, String description, String offerType, String publisher, String image, String url, int originalPrice, int discountPrice, OffsetDateTime start, OffsetDateTime end) {
		super(id, title, description, publisher, image, originalPrice, discountPrice, start, end, FreeGameType.EPIC_GAMES);

		this.url = url;
		this.offerType = offerType;
	}

	public boolean isDLC() {
		return this.offerType.equals("DLC");
	}

	public String getUrl() {
		return "https://www.epicgames.com/store/en-US/" + (this.offerType.equals("BUNDLE") ? "bundles" : "p") + "/" + this.url;
	}

	public String getRunUrl() {
		return "<com.epicgames.launcher://store/en-US/" + (this.offerType.equals("BUNDLE") ? "bundles" : "p") + "/" + this.url + ">";
	}

	public boolean isMysteryGame() {
		return this.getPublisher().equals("Epic Dev Test Account") && this.getTitle().equals("Mystery Game");
	}

	public Document toData() {
		return super.toData().append("url", this.url).append("offerType", this.offerType);
	}

	public static EpicFreeGame fromData(Document data) {
		String id = data.getString("id");
		String title = data.getString("title");
		String description = data.getString("description");
		String offerType = data.getString("offerType");
		String publisher = data.getEmbedded(List.of("seller", "name"), String.class);
		String url = data.get("catalogNs", Document.class).getList("mappings", Document.class, Collections.emptyList()).stream()
			.filter(d -> d.getString("pageType").equals("productHome"))
			.map(d -> d.getString("pageSlug"))
			.findFirst()
			.orElse(null);

		if (url == null) {
			url = offerType.equals("DLC") ? data.getString("urlSlug") : data.getString("productSlug");
		}

		Document promotion = FreeGameUtility.getBestPromotionalOffer(data);
		OffsetDateTime start = OffsetDateTime.parse(promotion.getString("startDate"));
		OffsetDateTime end = OffsetDateTime.parse(promotion.getString("endDate"));

		List<Document> keyImages = data.getList("keyImages", Document.class);

		String image = keyImages.stream()
			.filter(d -> d.getString("type").equals(publisher.equals("Epic Dev Test Account") && title.equals("Mystery Game") ? "VaultClosed" : "OfferImageWide"))
			.map(d -> d.getString("url"))
			.findFirst()
			.orElse(null);

		if (image == null) {
			image = keyImages.stream()
				.filter(d -> !d.getString("type").equals("VaultClosed"))
				.map(d -> d.getString("url"))
				.findFirst()
				.orElse(null);
		}

		// Because for some reason the url can sometimes be formatted incorrectly
		if (image != null) {
			String[] segments = image.split("/");
			for (int i = 3; i < segments.length; i++) {
				String old = segments[i];
				segments[i] = URLEncoder.encode(old, StandardCharsets.UTF_8);
			}

			image = String.join("/", segments);
		}

		Document priceInfo = data.getEmbedded(List.of("price", "totalPrice"), Document.class);
		int originalPrice = priceInfo.getInteger("originalPrice");
		int discountPrice = priceInfo.getInteger("discountPrice");

		return new EpicFreeGame(id, title, description, offerType, publisher, image, url, originalPrice, discountPrice, start, end);
	}

	public static EpicFreeGame fromDatabase(Document data) {
		String id = data.getString("gameId");
		String title = data.getString("title");
		String description = data.getString("description");
		String offerType = data.get("offerType", "OTHERS");
		String publisher = data.getString("publisher");
		String url = data.getString("url");
		String image = data.getString("image");

		Document promotion = data.get("promotion", Document.class);
		OffsetDateTime start = OffsetDateTime.ofInstant(Instant.ofEpochSecond(promotion.getLong("start")), ZoneOffset.UTC);
		OffsetDateTime end = OffsetDateTime.ofInstant(Instant.ofEpochSecond(promotion.getLong("end")), ZoneOffset.UTC);

		Document priceInfo = data.get("price", Document.class);
		int originalPrice = priceInfo.getInteger("original");
		int discountPrice = priceInfo.getInteger("discount");

		return new EpicFreeGame(id, title, description, offerType, publisher, image, url, originalPrice, discountPrice, start, end);
	}

}
