package com.sx4.bot.entities.info;

import com.sx4.bot.utility.FreeGameUtility;
import org.bson.Document;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

public class FreeGame {

	private final String id, title, description, publisher, image, url;
	private final int originalPrice, discountPrice;
	private final OffsetDateTime start, end;

	private FreeGame(String id, String title, String description, String publisher, String image, String url, int originalPrice, int discountPrice, OffsetDateTime start, OffsetDateTime end) {
		this.id = id;
		this.title = title;
		this.description = description;
		this.publisher = publisher;
		this.image = image;
		this.url = url;
		this.originalPrice = originalPrice;
		this.discountPrice = discountPrice;
		this.start = start;
		this.end = end;
	}

	public String getId() {
		return this.id;
	}

	public String getTitle() {
		return this.title;
	}

	public String getDescription() {
		return this.description;
	}

	public String getPublisher() {
		return this.publisher;
	}

	public String getImage() {
		return this.image;
	}

	public String getUrl() {
		return "https://www.epicgames.com/store/en-US/p/" + this.url;
	}

	public String getRunUrl() {
		return "<com.epicgames.launcher://store/en-US/p/" + this.url + ">";
	}

	public int getOriginalPrice() {
		return this.originalPrice;
	}

	public int getDiscountPrice() {
		return this.discountPrice;
	}

	public OffsetDateTime getPromotionStart() {
		return this.start;
	}

	public OffsetDateTime getPromotionEnd() {
		return this.end;
	}

	public boolean isMysteryGame() {
		return this.publisher.equals("Epic Dev Test Account") && this.title.equals("Mystery Game");
	}

	public Document toData() {
		return new Document("gameId", this.id)
			.append("title", this.title)
			.append("description", this.description)
			.append("publisher", this.publisher)
			.append("url", this.url)
			.append("image", this.image)
			.append("promotion", new Document("start", this.start.toEpochSecond()).append("end", this.end.toEpochSecond()))
			.append("price", new Document("original", this.originalPrice).append("discount", this.discountPrice));
	}

	public static FreeGame fromData(Document data) {
		String id = data.getString("id");
		String title = data.getString("title");
		String description = data.getString("description");
		String publisher = data.getEmbedded(List.of("seller", "name"), String.class);
		String url = data.getString("productSlug");

		Document promotion = FreeGameUtility.getPromotionalOffer(data);
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

		Document priceInfo = data.getEmbedded(List.of("price", "totalPrice"), Document.class);
		int originalPrice = priceInfo.getInteger("originalPrice");
		int discountPrice = priceInfo.getInteger("discountPrice");

		return new FreeGame(id, title, description, publisher, image, url, originalPrice, discountPrice, start, end);
	}

	public static FreeGame fromDatabase(Document data) {
		String id = data.getString("gameId");
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

		return new FreeGame(id, title, description, publisher, image, url, originalPrice, discountPrice, start, end);
	}

}
