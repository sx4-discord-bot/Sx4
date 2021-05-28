package com.sx4.bot.entities.economy.item;

import com.sx4.bot.managers.EconomyManager;
import org.bson.Document;

import java.util.List;

public class Tool extends Item {
	
	private final int currentDurability;
	private final int maxDurability;
	
	private final List<ItemStack<CraftItem>> craft;
	private final CraftItem repairItem;

	public Tool(EconomyManager manager, int id, String name, long price, ItemType type, int maxDurability, List<ItemStack<CraftItem>> craft, CraftItem repairItem) {
		this(manager, id, name, price, type, maxDurability, maxDurability, craft, repairItem);
	}
	
	public Tool(EconomyManager manager, int id, String name, long price, ItemType type, int currentDurability, int maxDurability, List<ItemStack<CraftItem>> craft, CraftItem repairItem) {
		super(manager, id, name, price, type);
		
		this.currentDurability = currentDurability;
		this.maxDurability = maxDurability;
		this.craft = craft;
		this.repairItem = repairItem;
	}

	public long getCurrentPrice() {
		return (this.getPrice() / this.maxDurability) * this.currentDurability;
	}
	
	public int getCurrentDurability() {
		return this.currentDurability;
	}
	
	public int getMaxDurability() {
		return this.maxDurability;
	}
	
	public List<ItemStack<CraftItem>> getCraft() {
		return this.craft;
	}
	
	public CraftItem getRepairItem() {
		return this.repairItem;
	}

	public Document toData() {
		return super.toData().append("currentDurability", this.getCurrentDurability());
	}
	
}
