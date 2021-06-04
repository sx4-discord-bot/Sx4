package com.sx4.bot.entities.economy.item;

import com.sx4.bot.managers.EconomyManager;
import org.bson.Document;

import java.util.List;

public class Rod extends Tool {
	
	private final long minYield;
	private final long maxYield;
	
	public Rod(Document data, Rod defaultRod) {
		this(
			defaultRod.getManager(),
			defaultRod.getId(),
			defaultRod.getName(), 
			data.get("price", defaultRod.getPrice()),
			data.get("durability", defaultRod.getDurability()),
			data.get("maxDurability", defaultRod.getMaxDurability()),
			defaultRod.getCraft(),
			defaultRod.getRepairItem(),
			data.get("minYield", defaultRod.getMinYield()),
			data.get("maxYield", defaultRod.getMaxYield())
		);
	}

	public Rod(EconomyManager manager, int id, String name, long price, int durability, int maxDurability, List<ItemStack<CraftItem>> craft, CraftItem repairItem, long minYield, long maxYield) {
		super(manager, id, name, price, ItemType.ROD, durability, maxDurability, craft, repairItem);
		
		this.minYield = minYield;
		this.maxYield = maxYield;
	}
	
	public Rod(EconomyManager manager, int id, String name, long price, int maxDurability, List<ItemStack<CraftItem>> craft, CraftItem repairItem, long minYield, long maxYield) {
		this(manager, id, name, price, maxDurability, maxDurability, craft, repairItem, minYield, maxYield);
	}

	public long getYield(EconomyManager manager) {
		return manager.getRandom().nextInt((int) (this.maxYield - this.minYield) + 1) + this.minYield;
	}
	
	public long getMinYield() {
		return this.minYield;
	}
	
	public long getMaxYield() {
		return this.maxYield;
	}

	public static Rod fromData(EconomyManager manager, Document data) {
		return new Rod(data, manager.getItemById(data.getInteger("id"), Rod.class));
	}
	
}
