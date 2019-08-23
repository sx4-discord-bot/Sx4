package com.sx4.economy.tools;

import com.sx4.economy.CraftingRecipe;
import com.sx4.economy.materials.Material;
import com.sx4.economy.materials.Wood;

public class Pickaxe extends Tool {
	
	public static final Pickaxe WOOD = new Pickaxe("Wooden Pickaxe", 250L, new CraftingRecipe(Wood.OAK, 3), Wood.OAK, 15, 25, 15, 0.2);
	public static final Pickaxe STONE = new Pickaxe("Stone Pickaxe", 800L, null, null, 30, 45, 25, 0.3);
	public static final Pickaxe COPPER = new Pickaxe("Copper Pickaxe", 1500L, new CraftingRecipe(Material.COPPER, 23, Wood.CHERRYWOOD, 6), Material.COPPER, 40, 60, 35, 0.42D);
	public static final Pickaxe BRONZE = new Pickaxe("Bronze Pickaxe", 2250L, new CraftingRecipe(Material.BRONZE, 20, Wood.CHERRYWOOD, 14), Material.BRONZE, 60, 72, 35, 0.53D);
	public static final Pickaxe GOLD = new Pickaxe("Gold Pickaxe", 5000L, new CraftingRecipe(Material.GOLD, 7, Wood.BIRCHWOOD, 5), Material.GOLD, 250, 400, 15, 0.6D);
	public static final Pickaxe IRON = new Pickaxe("Iron Pickaxe", 6000L, new CraftingRecipe(Material.IRON, 35, Wood.BIRCHWOOD, 12), Material.IRON, 98, 150, 60, 1.1D);
	public static final Pickaxe TITANIUM = new Pickaxe("Titanium Pickaxe", 10000L, new CraftingRecipe(Material.TITANIUM, 8, Wood.KINGWOOD, 8), Material.TITANIUM, 100, 175, 100, 1.6D);
	public static final Pickaxe URANIUM = new Pickaxe("Uranium Pickaxe", 55000L, new CraftingRecipe(Material.URANIUM, 8, Wood.KINGWOOD, 15), Material.URANIUM, 2600, 3100, 20, 2D);
	public static final Pickaxe BITCOIN = new Pickaxe("Bitcoin Pickaxe", 125000L, new CraftingRecipe(Material.BITCOIN, 5, Wood.SNAKEWOOD, 5), Material.BITCOIN, 2450, 2900, 50, 2.7D);
	public static final Pickaxe PLATINUM = new Pickaxe("Platinum Pickaxe", 425000L, new CraftingRecipe(Material.PLATINUM, 4, Wood.SNAKEWOOD, 10), Material.PLATINUM, 4900, 5700, 80, 3.1D);
	public static final Pickaxe SX4 = new Pickaxe("Sx4 Pickaxe", 1000000L, null, null, 4850, 5600, 200, 4D);
	
	public static final Pickaxe[] ALL = {WOOD, STONE, COPPER, BRONZE, GOLD, IRON, TITANIUM, URANIUM, BITCOIN, PLATINUM, SX4};
	
	private int minimumYield;
	private int maximumYield;
	private double multiplier;
	
	public Pickaxe(String name, Long price, CraftingRecipe craft, Material repairItem, int minimumYield, int maximumYield, int currentDurability, int durability, double multiplier, int upgrades) {
		super(name, price, craft, repairItem, currentDurability, durability, upgrades);
		
		this.minimumYield = minimumYield;
		this.maximumYield = maximumYield;
		this.multiplier = multiplier;
	}
	
	public Pickaxe(String name, Long price, CraftingRecipe craft, Material repairItem, int minimumYield, int maximumYield, int durability, double multiplier) {
		super(name, price, craft, repairItem, durability, durability);
		
		this.minimumYield = minimumYield;
		this.maximumYield = maximumYield;
		this.multiplier = multiplier;
	}
	
	public int getMinimumYield() {
		return this.minimumYield;
	}
	
	public int getMaximumYield() {
		return this.maximumYield;
	}
	
	public double getMultiplier() {
		return this.multiplier;
	}
	
	public Pickaxe getDefaultPickaxe() {
		for (Pickaxe pickaxe : Pickaxe.ALL) {
			if (pickaxe.getName().equals(this.getName())) {
				return pickaxe;
			}
		}
		
		return null;
	}
	
	public static Pickaxe getPickaxeByName(String pickaxeName) {
		pickaxeName = pickaxeName.toLowerCase();
			
		for (Pickaxe pickaxe : Pickaxe.ALL) {
			if (pickaxe.getName().toLowerCase().equals(pickaxeName)) {
				return pickaxe;
			}
		}
			
		for (Pickaxe pickaxe : Pickaxe.ALL) {
			if (pickaxe.getName().toLowerCase().startsWith(pickaxeName)) {
				return pickaxe;
			}
		}
			
		for (Pickaxe pickaxe : Pickaxe.ALL) {
			if (pickaxe.getName().toLowerCase().contains(pickaxeName)) {
				return pickaxe;
			}
		}
			
		return null;
	}
	
}
