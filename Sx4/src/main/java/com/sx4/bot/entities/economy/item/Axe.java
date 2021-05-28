package com.sx4.bot.entities.economy.item;

import com.sx4.bot.managers.EconomyManager;
import org.bson.Document;

import java.util.List;

public class Axe extends Tool {

	private final long maxMaterials;
	private final double multiplier;
	
	public Axe(Document data, Axe defaultAxe) {
		this(
			defaultAxe.getManager(),
			defaultAxe.getId(),
			defaultAxe.getName(), 
			data.get("maxPrice", defaultAxe.getPrice()),
			data.get("currentDurability", defaultAxe.getCurrentDurability()),
			data.get("maxDurability", defaultAxe.getMaxDurability()),
			defaultAxe.getCraft(),
			defaultAxe.getRepairItem(),
			data.get("maxMaterials", defaultAxe.getMaxMaterials()),
			data.get("multiplier", defaultAxe.getMultiplier())
		);
	}

	public Axe(EconomyManager manager, int id, String name, long price, int currentDurability, int maxDurability, List<ItemStack<CraftItem>> craft, CraftItem repairItem, long maxMaterials, double multiplier) {
		super(manager, id, name, price, ItemType.AXE, currentDurability, maxDurability, craft, repairItem);
		
		this.maxMaterials = maxMaterials;
		this.multiplier = multiplier;
	}
	
	public Axe(EconomyManager manager, int id, String name, long price, int maxDurability, List<ItemStack<CraftItem>> craft, CraftItem repairItem, long maxMaterials, double multiplier) {
		this(manager, id, name, price, maxDurability, maxDurability, craft, repairItem, maxMaterials, multiplier);
	}
	
	public double getMultiplier() {
		return this.multiplier;
	}
	
	public long getMaxMaterials() {
		return this.maxMaterials;
	}
	
}
