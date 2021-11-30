package com.sx4.bot.entities.info;

import com.sx4.bot.utility.FreeGameUtility;
import org.bson.Document;

import java.time.OffsetDateTime;
import java.util.List;

public class FreeGame {

	private final String title, description, publisher, image, url;
	private final double originalPrice, price;
	private final OffsetDateTime start, end;

	private FreeGame(Document data, boolean current) {
		this.title = data.getString("title");
		this.description = data.getString("description");
		this.publisher = data.getEmbedded(List.of("seller", "name"), String.class);
		this.url = "https://www.epicgames.com/store/en-US/p/" + data.getString("urlSlug");
		this.image = data.getList("keyImages", Document.class).stream()
			.filter(d -> d.getString("type").equals("OfferImageWide"))
			.map(d -> d.getString("url"))
			.findFirst()
			.orElse(null);

		Document promotion = current ? FreeGameUtility.getPromotionalOffer(data) : FreeGameUtility.getUpcomingPromotionalOffer(data);
		this.start = OffsetDateTime.parse(promotion.getString("startDate"));
		this.end = OffsetDateTime.parse(promotion.getString("endDate"));

		Document priceInfo = data.getEmbedded(List.of("price", "totalPrice"), Document.class);
		this.originalPrice = priceInfo.getInteger("originalPrice") / 100D;
		this.price = priceInfo.getInteger("discountPrice") / 100D;
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
		return this.url;
	}

	public double getOriginalPrice() {
		return this.originalPrice;
	}

	public double getPrice() {
		return this.price;
	}

	public OffsetDateTime getStart() {
		return this.start;
	}

	public OffsetDateTime getEnd() {
		return this.end;
	}

	public static FreeGame fromData(Document data) {
		return FreeGame.fromData(data, false);
	}

	public static FreeGame fromData(Document data, boolean current) {
		return new FreeGame(data, current);
	}

}
