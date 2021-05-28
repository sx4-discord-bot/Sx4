package com.sx4.bot.entities.economy.item;

import com.sx4.bot.managers.EconomyManager;
import org.bson.Document;

public class Wood extends CraftItem {
	
	public Wood(Document data, Wood defaultWood) {
		this(defaultWood.getManager(), defaultWood.getId(), defaultWood.getName(), defaultWood.getPrice());
	}

	public Wood(EconomyManager manager, int id, String name, long price) {
		super(manager, id, name, price, ItemType.WOOD);
	}
	
}
