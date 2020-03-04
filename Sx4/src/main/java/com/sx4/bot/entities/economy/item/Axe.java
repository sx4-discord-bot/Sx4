package com.sx4.bot.entities.economy.item;

import java.util.List;

public class Axe extends Tool {

	private final long maxMaterials;
	private final double multiplier;

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
