package com.sx4.bot.entities.economy.item;

import org.bson.Document;

public class Wood extends Material {
	
	public Wood(Document data, Wood defaultWood) {
		this(defaultWood.getName(), defaultWood.getPrice());
	}

	public Wood(String name, long price) {
		super(name, price, ItemType.WOOD, null, false);
	}
	
}
