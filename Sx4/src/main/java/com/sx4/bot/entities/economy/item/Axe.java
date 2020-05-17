package com.sx4.bot.entities.economy.item;

import java.util.List;

import org.bson.Document;

public class Axe extends Tool {

	private final long maxMaterials;
	private final double multiplier;
	
	public Axe(Document data, Axe defaultAxe) {
		this(
			defaultAxe.getName(), 
			data.get("price", defaultAxe.getPrice()),
			data.get("currentDurability", defaultAxe.getCurrentDurability()), 
			data.get("maxDurability", defaultAxe.getMaxDurability()), 
			defaultAxe.getCraft(),
			defaultAxe.getRepairItem(),
			data.get("maxMaterials", defaultAxe.getMaxMaterials()),
			data.get("multiplier", defaultAxe.getMultiplier())
		);
	}

	public Axe(String name, long price, long currentDurability, long maxDurability, List<ItemStack<Material>> craft, Material repairItem, long maxMaterials, double multiplier) {
		super(name, price, ItemType.AXE, currentDurability, maxDurability, craft, repairItem);
		
		this.maxMaterials = maxMaterials;
		this.multiplier = multiplier;
	}
	
	public Axe(String name, long price, long maxDurability, List<ItemStack<Material>> craft, Material repairItem, long maxMaterials, double multiplier) {
		this(name, price, maxDurability, maxDurability, craft, repairItem, maxMaterials, multiplier);
	}
	
	public double getMultiplier() {
		return this.multiplier;
	}
	
	public long getMaxMaterials() {
		return this.maxMaterials;
	}
	
}
