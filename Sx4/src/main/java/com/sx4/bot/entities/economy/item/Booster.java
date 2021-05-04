package com.sx4.bot.entities.economy.item;

import org.bson.Document;

public class Booster extends Item {
	
	public Booster(Document data, Booster defaultBooster) {
		this(defaultBooster.getId(), defaultBooster.getName(), defaultBooster.getPrice());
	}

	public Booster(int id, String name, long price) {
		super(id, name, price, ItemType.BOOSTER);
	}
	
}
