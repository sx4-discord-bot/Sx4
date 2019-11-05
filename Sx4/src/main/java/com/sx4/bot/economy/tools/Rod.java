package com.sx4.bot.economy.tools;

import com.sx4.bot.economy.CraftingRecipe;
import com.sx4.bot.economy.materials.Material;
import com.sx4.bot.economy.materials.Wood;

public class Rod extends Tool {
	
	public static final Rod STONE = new Rod("Stone Rod", 600L, null, null, 32, 35, 20);
	public static final Rod COPPER = new Rod("Copper Rod", 1000L, new CraftingRecipe(Material.COPPER, 14, Wood.BIRCHWOOD, 1), Material.COPPER, 37, 46, 25);
	public static final Rod BRONZE = new Rod("Bronze Rod", 1300L, new CraftingRecipe(Material.BRONZE, 8, Wood.BIRCHWOOD, 2), Material.BRONZE, 50, 58, 25);
	public static final Rod GOLD = new Rod("Gold Rod", 2400L, new CraftingRecipe(Material.GOLD, 3, Wood.OAK, 8), Material.GOLD, 240, 290, 10);
	public static final Rod IRON = new Rod("Iron Rod", 3300L, new CraftingRecipe(Material.IRON, 15, Wood.SNAKEWOOD, 2), Material.IRON, 55, 75, 55);
	public static final Rod TITANIUM = new Rod("Titanium Rod", 6500L, new CraftingRecipe(Material.TITANIUM, 4, Wood.CHERRYWOOD, 8), Material.TITANIUM, 104, 120, 60);
	public static final Rod URANIUM = new Rod("Uranium Rod", 15000L, new CraftingRecipe(Material.URANIUM, 2, Wood.OAK, 20), Material.URANIUM, 550, 800, 25);
	public static final Rod BITCOIN = new Rod("Bitcoin Rod", 45000L, new CraftingRecipe(Material.BITCOIN, 2, Wood.SNAKEWOOD, 10), Material.BITCOIN, 1350, 2000, 30);
	public static final Rod PLATINUM = new Rod("Platinum Rod", 90000L, new CraftingRecipe(Material.PLATINUM, 1, Wood.CHERRYWOOD, 14), Material.PLATINUM, 1750, 2350, 50);
	public static final Rod SX4 = new Rod("Sx4 Rod", 250000L, null, null, 3175, 3900, 75);
	
	public static final Rod[] ALL = {STONE, COPPER, BRONZE, GOLD, IRON, TITANIUM, URANIUM, BITCOIN, PLATINUM, SX4};
	
	private int minimumYield;
	private int maximumYield;
	
	public Rod(String name, Long price, CraftingRecipe craft, Material repairItem, int minimumYield, int maximumYield, int currentDurability, int durability, int upgrades) {
		super(name, price, craft, repairItem, currentDurability, durability, upgrades);
		
		this.minimumYield = minimumYield;
		this.maximumYield = maximumYield;
	}
	
	public Rod(String name, Long price, CraftingRecipe craft, Material repairItem, int minimumYield, int maximumYield, int durability) {
		super(name, price, craft, repairItem, durability, durability);
		
		this.minimumYield = minimumYield;
		this.maximumYield = maximumYield;
	}
	
	public int getMinimumYield() {
		return this.minimumYield;
	}
	
	public int getMaximumYield() {
		return this.maximumYield;
	}
	
	public Rod getDefaultRod() {
		for (Rod rod : Rod.ALL) {
			if (rod.getName().equals(this.getName())) {
				return rod;
			}
		}
		
		return null;
	}
	
	public static Rod getRodByName(String rodName) {
		rodName = rodName.toLowerCase();
		
		for (Rod rod : Rod.ALL) {
			if (rod.getName().toLowerCase().equals(rodName)) {
				return rod;
			}
		}
		
		for (Rod rod : Rod.ALL) {
			if (rod.getName().toLowerCase().startsWith(rodName)) {
				return rod;
			}
		}
		
		for (Rod rod : Rod.ALL) {
			if (rod.getName().toLowerCase().contains(rodName)) {
				return rod;
			}
		}
		
		return null;
	}
	
}
