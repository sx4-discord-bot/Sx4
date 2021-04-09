package com.sx4.bot.entities.economy.item;

import org.bson.Document;

import java.util.List;
import java.util.stream.Collectors;
public class ItemStack<Type extends Item> implements Comparable<ItemStack<Type>> {
	
	private final Type item;
	private long amount;
	
	@SuppressWarnings("unchecked")
	public ItemStack(Document data) {
		Item defaultItem = null; //Item.getFromName(data.getString("name"));
		ItemType type = ItemType.fromType(data.getInteger("type"));
		
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
	
	public String getName() {
		return this.item.getName();
	}
	
	public ItemStack<Type> addAmount(long amount) {
		this.amount += amount;
		
		return this;
	}
	
	public ItemStack<Type> removeAmount(long amount) {
		return this.addAmount(-amount);
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
	
	public ItemStack<Type> combine(ItemStack<?> itemStack) {
		if (!itemStack.equalsItem(this)) {
			throw new IllegalArgumentException("Cannot combine item stacks which contain different items");
		}
		
		return this.addAmount(itemStack.getAmount());
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

	@Override
	public int compareTo(ItemStack<Type> itemStack) {
		return Long.compare(this.amount, itemStack.getAmount());
	}
	
}
