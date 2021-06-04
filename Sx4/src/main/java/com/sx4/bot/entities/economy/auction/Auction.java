package com.sx4.bot.entities.economy.auction;

import com.sx4.bot.entities.economy.item.Item;
import com.sx4.bot.entities.economy.item.ItemStack;
import com.sx4.bot.managers.EconomyManager;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.time.Clock;

public class Auction<Type extends Item> {

	private final ObjectId id;
	
	private final long price, expires, ownerId;
	
	private final ItemStack<Type> itemStack;

	public Auction(EconomyManager manager, Document data) {
		this(data.getObjectId("_id"), data.getLong("price"), data.getLong("expires"), data.get("ownerId", 0L), new ItemStack<>(manager, data));
	}

	public Auction(long price, long expires, long ownerId, ItemStack<Type> itemStack) {
		this(null, price, expires, ownerId, itemStack);
	}
	
	public Auction(ObjectId id, long price, long expires, long ownerId, ItemStack<Type> itemStack) {
		this.id = id;
		this.price = price;
		this.ownerId = ownerId;
		this.expires = expires;
		this.itemStack = itemStack;
	}
	
	public ObjectId getId() {
		return this.id;
	}
	
	public long getTimestamp() {
		return this.id.getTimestamp();
	}
	
	public String getHex() {
		return this.id.toHexString();
	}
	
	public long getPrice() {
		return this.price;
	}

	public double getPricePerItem() {
		return (double) this.price / this.itemStack.getAmount();
	}
	
	public long getExpiresAt() {
		return this.expires;
	}

	public long getOwnerId() {
		return this.ownerId;
	}
	
	public long getTimeRemaining() {
		return Clock.systemUTC().instant().getEpochSecond() - (this.getTimestamp() + this.expires);
	}
	
	public ItemStack<Type> getItemStack() {
		return this.itemStack;
	}

	public Document toData() {
		Document data = new Document("price", this.price)
			.append("expires", this.expires)
			.append("ownerId", this.ownerId)
			.append("amount", this.itemStack.getAmount())
			.append("item", this.itemStack.getItem().toData());

		if (this.id != null) {
			data.append("_id", this.id);
		}

		return data;
	}
	
}
