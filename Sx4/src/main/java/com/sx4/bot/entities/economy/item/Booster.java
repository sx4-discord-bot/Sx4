package com.sx4.bot.entities.economy.item;

import org.bson.Document;

public class Booster extends Item {
	
	public Booster(Document data, Booster defaultBooster) {
		this(defaultBooster.getName(), defaultBooster.getPrice());
	}

	public Booster(String name, long price) {
		super(name, price, ItemType.BOOSTER);
	}
	
}
