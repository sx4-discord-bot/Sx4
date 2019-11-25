package com.sx4.bot.economy;

import com.sx4.bot.utils.EconomyUtils;

public class Item {
	
	private String name;
	private Long price;
	
	public Item(String name, Long price) {
		this.name = name;
		this.price = price;
	}
	
	public String getName() {
		return this.name;
	}
	
	public boolean isBuyable() {
		return this.price != null;
	}
	
	public Long getPrice() {
		return this.price;
	}
	
	public static Item getItemByName(String name) {
		name = name.toLowerCase();
		for (Item item : EconomyUtils.ALL_ITEMS) {
			if (item.getName().toLowerCase().equals(name)) {
				return item;
			}
		}
		
		return null;
	}
	
}
