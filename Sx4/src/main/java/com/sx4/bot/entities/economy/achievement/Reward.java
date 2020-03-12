package com.sx4.bot.entities.economy.achievement;

import java.util.List;
import java.util.stream.Collectors;

import com.sx4.bot.entities.economy.item.ItemStack;

public class Reward {

	private final long experience;
	
	private final long money;
	private final List<ItemStack<?>> items;
	
	public Reward(long experience, long money, List<ItemStack<?>> items) {
		this.experience = experience;
		this.money = money;
		this.items = items;
	}
	
	public long getExperience() {
		return this.experience;
	}
	
	public long getMoney() {
		return this.money;
	}
	
	public List<ItemStack<?>> getItems() {
		return this.items;
	}
	
	public String toString() {
		return String.format("%,d EXP", this.experience) 
			+ (this.money != 0 ? String.format("\n$%,d", this.money) : "") 
			+ (!this.items.isEmpty() ? "\n" + this.items.stream().map(ItemStack::toString).collect(Collectors.joining("\n")) : "");
	}
	
}
