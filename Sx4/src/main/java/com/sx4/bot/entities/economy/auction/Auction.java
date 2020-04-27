package com.sx4.bot.entities.economy.auction;

import java.time.Clock;
import java.util.concurrent.CompletableFuture;

import org.bson.types.ObjectId;

import com.mongodb.client.result.DeleteResult;
import com.sx4.bot.database.Database;
import com.sx4.bot.entities.economy.item.Item;
import com.sx4.bot.entities.economy.item.ItemStack;

public class Auction<Type extends Item> {

	private final ObjectId id;
	
	private final long price;
	private final long timeout;
	
	private final ItemStack<Type> itemStack;
	
	public Auction(ObjectId id, long price, long timeout, ItemStack<Type> itemStack) {
		this.id = id;
		this.price = price;
		this.timeout = timeout;
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
	
	public long getTimeout() {
		return this.timeout;
	}
	
	public long getTimeRemaining() {
		return Clock.systemUTC().instant().getEpochSecond() - (this.getTimestamp() + this.timeout);
	}
	
	public ItemStack<Type> getItemStack() {
		return this.itemStack;
	}
	
	public CompletableFuture<DeleteResult> delete() {
		return Database.get().deleteAuctionById(this.id);
	}
	
}
