package com.sx4.bot.entities.economy.item;

import org.bson.Document;

public class Wood extends Material {
	
	public Wood(Document data, Wood defaultWood) {
		this(defaultWood.getId(), defaultWood.getName(), defaultWood.getPrice());
	}

	public Wood(int id, String name, long price) {
		super(id, name, price, ItemType.WOOD, null, false);
	}
	
}
