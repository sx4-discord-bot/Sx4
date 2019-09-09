package com.sx4.bot.economy.tools;

import com.sx4.bot.economy.CraftingRecipe;
import com.sx4.bot.economy.Item;
import com.sx4.bot.economy.ItemStack;
import com.sx4.bot.economy.materials.Material;

public class Tool extends Item {
	
	private CraftingRecipe craft;
	private int durability;
	private Material repairItem;
	private int upgrades = 0;
	private int currentDurability;
	
	public Tool(String name, long price, CraftingRecipe craft, Material repairItem, int currentDurability, int durability) {
		super(name, price);

		this.craft = craft;
		this.durability = durability;
		this.repairItem = repairItem;
		this.currentDurability = currentDurability;
	}

	public Tool(String name, long price, CraftingRecipe craft, Material repairItem, int currentDurability, int durability, int upgrades) {
		super(name, price);

		this.craft = craft;
		this.durability = durability;
		this.repairItem = repairItem;
		this.upgrades = upgrades;
		this.currentDurability = currentDurability;
	}
	
	public int getCurrentDurability() {
		return this.currentDurability;
	}
	
	public int getUpgrades() {
		return this.upgrades;
	}
	
	public boolean isCraftable() {
		return this.craft != null;
	}
	
	public CraftingRecipe getCraftingRecipe() {
		return this.craft;
	}
	
	public long getEstimatePrice() {
		if (!this.isBuyable()) {
			long price = 0;
			for (ItemStack itemStack : this.craft.getCraftingItems()) {
				price += itemStack.getItem().getPrice();
			}
			
			return price;
		} else {
			return this.getPrice();
		}
	}
	
	public int getDurability() {
		return this.durability;
	}
	
	public boolean isRepairable() {
		return this.repairItem != null;
	}
	
	public Material getRepairItem() {
		return this.repairItem;
	}
	
	public int getAmountOfMaterialsForRepair(int durabilityNeeded) {
		return (int) Math.ceil((((double) this.getEstimatePrice() / this.repairItem.getPrice()) / this.durability) * durabilityNeeded);
	}
	
	public long getEstimateOfDurability(long materialAmount) {
		return (long) Math.floor(materialAmount / (((double) this.getEstimatePrice() / this.repairItem.getPrice()) / this.durability));
	}
	
}
