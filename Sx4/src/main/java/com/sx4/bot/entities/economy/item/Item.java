package com.sx4.bot.entities.economy.item;

import com.sx4.bot.managers.EconomyManager;
import org.bson.Document;

public class Item {

	private final EconomyManager manager;

	private final long price;
	private final String name;
	private final int id;
	//private final boolean purchasable;
	private final ItemType type;
	
	public Item(EconomyManager manager, int id, String name, long price, /*boolean purchasable,*/ ItemType type) {
		this.manager = manager;
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

	public EconomyManager getManager() {
		return this.manager;
	}
	
	public Document toData() {
		return new Document("id", this.id)
			.append("name", this.name)
			.append("price", this.price)
			.append("type", this.type.getId());
	}
	
}
