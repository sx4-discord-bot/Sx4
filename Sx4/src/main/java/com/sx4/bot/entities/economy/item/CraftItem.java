package com.sx4.bot.entities.economy.item;

import com.sx4.bot.managers.EconomyManager;
import org.bson.Document;

public class CraftItem extends Item {

	public CraftItem(Document data, CraftItem defaultCraftItem) {
		this(defaultCraftItem.getManager(), defaultCraftItem.getId(), defaultCraftItem.getName(), defaultCraftItem.getPrice(), defaultCraftItem.getType());
	}

	public CraftItem(EconomyManager manager, int id, String name, long price, ItemType type) {
		super(manager, id, name, price, type);
	}

}
