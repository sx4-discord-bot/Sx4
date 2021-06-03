package com.sx4.bot.entities.economy.auction;

import com.sx4.bot.entities.economy.item.Item;
import com.sx4.bot.entities.economy.item.ItemStack;
import com.sx4.bot.managers.EconomyManager;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.time.Clock;

public class Auction<Type extends Item> {

	private final ObjectId id;
	
	private final long price;
	private final long timeout;
	
	private final ItemStack<Type> stack;

	public Auction(EconomyManager manager, Document data) {
		this(data.getObjectId("_id"), data.getLong("price"), data.getLong("timeout"), new ItemStack<>(manager, data.get("stack", Document.class)));
	}

	public Auction(long price, long timeout, ItemStack<Type> stack) {
		this(null, price, timeout, stack);
	}
	
	public Auction(ObjectId id, long price, long timeout, ItemStack<Type> itemStack) {
		this.id = id;
		this.price = price;
		this.timeout = timeout;
		this.stack = itemStack;
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
	
	public long getTimeout() {
		return this.timeout;
	}
	
	public long getTimeRemaining() {
		return Clock.systemUTC().instant().getEpochSecond() - (this.getTimestamp() + this.timeout);
	}
	
	public ItemStack<Type> getStack() {
		return this.stack;
	}

	public Document toData() {
		Document data = new Document("price", this.price)
			.append("timeout", this.timeout)
			.append("stack", this.stack.toData());

		if (this.id != null) {
			data.append("_id", this.id);
		}

		return data;
	}
	
}
