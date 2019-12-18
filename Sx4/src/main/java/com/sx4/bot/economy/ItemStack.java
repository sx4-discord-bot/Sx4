package com.sx4.bot.economy;

public class ItemStack<Type extends Item> {
		
	private final Type item;
	private long amount;

	public ItemStack(Type item, long amount) {
		this.item = item;
		this.amount = amount;
	}
	
	public String toString() {
		return String.format("%,d %s", this.amount, this.item.getName());
	}
	
	public Type getItem() {
		return this.item;
	}
	
	public long getAmount() {
		return this.amount;
	}
	
	public Long getPrice() {
		return this.item.isBuyable() ? this.item.getPrice() * this.amount : null;
	}
	
	public ItemStack<Type> incrementAmount() {
		return this.addAmount(1L);
	}
	
	public ItemStack<Type> addAmount(long amount) {
		this.amount += amount;
		
		return this;
	}
	
	public ItemStack<Type> decrementAmount() {
		return this.removeAmount(1L);	
	}
	
	public ItemStack<Type> removeAmount(long amount) {
		this.amount -= amount;
		
		return this;
	}
	
}
