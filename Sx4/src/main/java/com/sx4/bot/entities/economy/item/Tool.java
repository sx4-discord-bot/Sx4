package com.sx4.bot.entities.economy.item;

import com.sx4.bot.managers.EconomyManager;
import org.bson.Document;

import java.util.List;

public class Tool extends Item {

	private final int upgrades;
	
	private final int durability;
	private final int maxDurability;
	
	private final List<ItemStack<CraftItem>> craft;
	private final CraftItem repairItem;

	public Tool(EconomyManager manager, int id, String name, long price, ItemType type, int maxDurability, int upgrades, List<ItemStack<CraftItem>> craft, CraftItem repairItem) {
		this(manager, id, name, price, type, maxDurability, maxDurability, upgrades, craft, repairItem);
	}
	
	public Tool(EconomyManager manager, int id, String name, long price, ItemType type, int durability, int maxDurability, int upgrades, List<ItemStack<CraftItem>> craft, CraftItem repairItem) {
		super(manager, id, name, price, type);
		
		this.durability = durability;
		this.maxDurability = maxDurability;
		this.upgrades = upgrades;
		this.craft = craft;
		this.repairItem = repairItem;
	}

	public int getUpgrades() {
		return this.upgrades;
	}

	public long getCurrentPrice() {
		return (this.getPrice() / this.maxDurability) * this.durability;
	}
	
	public int getDurability() {
		return this.durability;
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
		return super.toData()
			.append("durability", this.getDurability())
			.append("maxDurability", this.getMaxDurability());
	}
	
}
