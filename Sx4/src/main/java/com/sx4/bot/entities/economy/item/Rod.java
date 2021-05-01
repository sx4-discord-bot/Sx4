package com.sx4.bot.entities.economy.item;

import org.bson.Document;

import java.util.List;
import java.util.Random;

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

	public Rod(String name, long price, int currentDurability, int maxDurability, List<ItemStack<Material>> craft, Material repairItem, long minYield, long maxYield) {
		super(name, price, ItemType.ROD, currentDurability, maxDurability, craft, repairItem);
		
		this.minYield = minYield;
		this.maxYield = maxYield;
	}
	
	public Rod(String name, long price, int maxDurability, List<ItemStack<Material>> craft, Material repairItem, long minYield, long maxYield) {
		this(name, price, maxDurability, maxDurability, craft, repairItem, minYield, maxYield);
	}

	public long getYield(Random random) {
		return random.nextInt((int) (this.maxYield - this.minYield) + 1) + this.minYield;
	}
	
	public long getMinYield() {
		return this.minYield;
	}
	
	public long getMaxYield() {
		return this.maxYield;
	}
	
}
