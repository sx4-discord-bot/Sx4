package com.sx4.bot.economy;

public class ItemStack {
		
	private Item item;
	private long amount;

	public ItemStack(Item item, long amount) {
		this.item = item;
		this.amount = amount;
	}
	
	public String toString() {
		return String.format("%,d %s", this.amount, this.item.getName());
	}
	
	public Item getItem() {
		return this.item;
	}
	
	public long getAmount() {
		return this.amount;
	}
	
	public ItemStack incrementAmount() {
		return this.addAmount(1L);
	}
	
	public ItemStack addAmount(long amount) {
		this.amount += amount;
		
		return this;
	}
	
	public ItemStack decrementAmount() {
		return this.removeAmount(1L);	
	}
	
	public ItemStack removeAmount(long amount) {
		this.amount -= amount;
		
		return this;
	}
	
}
