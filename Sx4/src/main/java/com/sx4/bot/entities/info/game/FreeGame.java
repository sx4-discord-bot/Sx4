package com.sx4.bot.entities.info.game;

import org.bson.Document;

import java.time.OffsetDateTime;

public abstract class FreeGame<Type> {

	private final Type id;
	private final String title, description, publisher, image;
	private final int originalPrice, discountPrice;
	private final OffsetDateTime start, end;
	private final FreeGameType type;

	protected FreeGame(Type id, String title, String description, String publisher, String image, int originalPrice, int discountPrice, OffsetDateTime start, OffsetDateTime end, FreeGameType type) {
		this.id = id;
		this.title = title;
		this.description = description;
		this.publisher = publisher;
		this.image = image;
		this.originalPrice = originalPrice;
		this.discountPrice = discountPrice;
		this.start = start;
		this.end = end;
		this.type = type;
	}

	public Type getId() {
		return this.id;
	}

	public FreeGameType getType() {
		return this.type;
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

	public abstract String getUrl();

	public abstract String getRunUrl();

	public int getOriginalPrice() {
		return this.originalPrice;
	}

	public double getOriginalPriceDecimal() {
		return this.originalPrice / 100D;
	}

	public int getDiscountPrice() {
		return this.discountPrice;
	}

	public double getDiscountPriceDecimal() {
		return this.discountPrice / 100D;
	}

	public OffsetDateTime getPromotionStart() {
		return this.start;
	}

	public OffsetDateTime getPromotionEnd() {
		return this.end;
	}

	public Document toData() {
		return new Document("gameId", this.id)
			.append("title", this.title)
			.append("description", this.description)
			.append("publisher", this.publisher)
			.append("image", this.image)
			.append("promotion", new Document("start", this.start.toEpochSecond()).append("end", this.end.toEpochSecond()))
			.append("price", new Document("original", this.originalPrice).append("discount", this.discountPrice))
			.append("type", this.type.getId());
	}

}
