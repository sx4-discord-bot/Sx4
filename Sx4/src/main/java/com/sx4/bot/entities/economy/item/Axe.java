package com.sx4.bot.entities.economy.item;

import org.bson.Document;

import java.util.List;

public class Axe extends Tool {

	private final long maxMaterials;
	private final double multiplier;
	
	public Axe(Document data, Axe defaultAxe) {
		this(
			defaultAxe.getId(),
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

	public Axe(int id, String name, long price, int currentDurability, int maxDurability, List<ItemStack<Material>> craft, Material repairItem, long maxMaterials, double multiplier) {
		super(id, name, price, ItemType.AXE, currentDurability, maxDurability, craft, repairItem);
		
		this.maxMaterials = maxMaterials;
		this.multiplier = multiplier;
	}
	
	public Axe(int id, String name, long price, int maxDurability, List<ItemStack<Material>> craft, Material repairItem, long maxMaterials, double multiplier) {
		this(id, name, price, maxDurability, maxDurability, craft, repairItem, maxMaterials, multiplier);
	}
	
	public double getMultiplier() {
		return this.multiplier;
	}
	
	public long getMaxMaterials() {
		return this.maxMaterials;
	}
	
}
