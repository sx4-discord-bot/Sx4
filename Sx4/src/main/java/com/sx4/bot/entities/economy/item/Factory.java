package com.sx4.bot.entities.economy.item;

import org.bson.Document;

public class Factory extends Item {
	
	private final long maxYield;
	private final long minYield;
	private final ItemStack<Material> cost;
	
	public Factory(Document data, Factory defaultFactory) {
		this(defaultFactory.getName(), defaultFactory.getCost(), defaultFactory.getMinYield(), defaultFactory.getMaxYield());
	}

	public Factory(String name, ItemStack<Material> cost, long minYield, long maxYield) {
		super(name, cost.getItem().getPrice() * cost.getAmount(), ItemType.FACTORY);
		
		this.cost = cost;
		this.maxYield = maxYield;
		this.minYield = minYield;
	}
	
	public long getMaxYield() {
		return this.maxYield;
	}
	
	public long getMinYield() {
		return this.minYield;
	}
	
	public ItemStack<Material> getCost() {
		return this.cost;
	}
	
}
