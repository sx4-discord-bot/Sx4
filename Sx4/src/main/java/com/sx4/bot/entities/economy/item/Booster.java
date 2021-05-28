package com.sx4.bot.entities.economy.item;

import com.sx4.bot.managers.EconomyManager;
import org.bson.Document;

public class Booster extends Item {
	
	public Booster(Document data, Booster defaultBooster) {
		this(defaultBooster.getManager(), defaultBooster.getId(), defaultBooster.getName(), defaultBooster.getPrice());
	}

	public Booster(EconomyManager manager, int id, String name, long price) {
		super(manager, id, name, price, ItemType.BOOSTER);
	}
	
}
