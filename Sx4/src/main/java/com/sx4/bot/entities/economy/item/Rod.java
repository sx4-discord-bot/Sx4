package com.sx4.bot.entities.economy.item;

import java.util.List;

import org.bson.Document;

public class Rod extends Tool {
	
	private final long minYield;
	private final long maxYield;
	
	public Rod(Document data, Rod defaultRod) {
		this(
			defaultRod.getName(), 
			data.get("price", defaultRod.getPrice()),
			data.get("currentDurability", defaultRod.getCurrentDurability()), 
			data.get("maxDurability", defaultRod.getMaxDurability()), 
			defaultRod.getCraft(),
			defaultRod.getRepairItem(),
			data.get("minYield", defaultRod.getMinYield()),
			data.get("maxYield", defaultRod.getMaxYield())
		);
	}

	public Rod(String name, long price, long currentDurability, long maxDurability, List<ItemStack<Material>> craft, Material repairItem, long minYield, long maxYield) {
		super(name, price, ItemType.ROD, currentDurability, maxDurability, craft, repairItem);
		
		this.minYield = minYield;
		this.maxYield = maxYield;
	}
	
	public Rod(String name, long price, long maxDurability, List<ItemStack<Material>> craft, Material repairItem, long minYield, long maxYield) {
		this(name, price, maxDurability, maxDurability, craft, repairItem, minYield, maxYield);
	}
	
	public long getMinYield() {
		return this.minYield;
	}
	
	public long getMaxYield() {
		return this.maxYield;
	}
	
}
