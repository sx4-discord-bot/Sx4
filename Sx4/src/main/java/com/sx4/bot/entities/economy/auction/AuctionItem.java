package com.sx4.bot.entities.economy.auction;

import com.sx4.bot.entities.economy.Item;
import com.sx4.bot.entities.economy.ItemStack;

public class AuctionItem<Type extends Item> {

	private final long price;
	private final ItemStack<Type> itemStack;
	
	public AuctionItem(long price, ItemStack<Type> itemStack) {
		this.price = price;
		this.itemStack = itemStack;
	}
	
	public long getPrice() {
		return this.price;
	}
	
	public ItemStack<Type> getItemStack() {
		return this.itemStack;
	}
	
}
