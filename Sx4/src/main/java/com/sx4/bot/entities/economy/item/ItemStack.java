package com.sx4.bot.entities.economy.item;

import java.util.List;
import java.util.stream.Collectors;

import org.bson.Document;

public class ItemStack<Type extends Item> {
	
	private final Type item;
	private long amount;
	
	@SuppressWarnings("unchecked")
	public ItemStack(Document data) {
		Item defaultItem = Item.getFromName(data.getString("name"));
		ItemType type = ItemType.getFromType(data.getInteger("type"));
		
		this.item = (Type) type.create(data, defaultItem);
		this.amount = data.getLong("amount");
	}

	public ItemStack(Type item, long amount) {
		this.item = item;
		this.amount = amount;
	}
	
	public Type getItem() {
		return this.item;
	}
	
	public void addAmount(long amount) {
		this.amount += amount;
	}
	
	public void removeAmount(long amount) {
		this.amount -= amount;
	}
	
	public long getAmount() {
		return this.amount;
	}
	
	public boolean equalsItem(ItemStack<?> itemStack) {
		if (this.item.getName().equals(itemStack.getItem().getName())) {
			return true;
		}
		
		return false;
	}
	
	public int compareTo(ItemStack<?> itemStack) {
		return Long.compare(this.amount, itemStack.getAmount());
	}
	
	public String toString() {
		return this.item.getName() + " x" + this.amount;
	}
	
	public Document toData() {
		return this.item.toData()
			.append("amount", this.amount);
	}
	
	public static List<ItemStack<? extends Item>> fromData(List<Document> data) {
		return data.stream().map(ItemStack::new).collect(Collectors.toList());
	}
	
}
