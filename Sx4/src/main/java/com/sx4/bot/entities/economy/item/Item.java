package com.sx4.bot.entities.economy.item;

import org.bson.Document;

public class Item {

	private final long price;
	private final String name;
	private final int id;
	//private final boolean purchasable;
	private final ItemType type;
	
	public Item(int id, String name, long price, /*boolean purchasable,*/ ItemType type) {
		this.price = price;
		this.name = name;
		this.id = id;
		//this.purchasable = purchasable;
		this.type = type;
	}
	
	public long getPrice() {
		return this.price;
	}
	
	/*public boolean isPurchasable() {
		return this.purchasable;
	}*/
	
	public String getName() {
		return this.name;
	}

	public int getId() {
		return this.id;
	}
	
	public ItemType getType() {
		return this.type;
	}
	
	public Document toData() {
		return new Document("name", this.name)
			.append("type", this.type.getType());
	}
	
}
