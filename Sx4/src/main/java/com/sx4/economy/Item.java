package com.sx4.economy;

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
	
}
