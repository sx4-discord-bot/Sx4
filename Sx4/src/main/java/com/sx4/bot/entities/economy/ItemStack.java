package com.sx4.bot.entities.economy;

import java.util.List;
import java.util.stream.Collectors;

import org.bson.Document;

public class ItemStack<Type extends Item> {
	
	private final Type item;
	private final long amount;
	
	@SuppressWarnings("unchecked")
	public ItemStack(Document data) {
		this.item = (Type) new Item("a", 0L, ItemType.AXE);
		this.amount = data.getLong("amount");
	}

	public ItemStack(Type item, long amount) {
		this.item = item;
		this.amount = amount;
	}
	
	public Type getItem() {
		return this.item;
	}
	
	public long getAmount() {
		return this.amount;
	}
	
	public static List<ItemStack<?>> fromData(List<Document> data) {
		return data.stream().map(ItemStack::new).collect(Collectors.toList());
	}
	
}
