package com.sx4.economy.tools;

import java.util.HashMap;
import java.util.Map;

import com.sx4.economy.CraftingRecipe;
import com.sx4.economy.materials.Material;
import com.sx4.economy.materials.Wood;

public class Axe extends Tool {
	
	public static final Axe WOOD = new Axe("Wooden Axe", 180L, new CraftingRecipe(Wood.OAK, 2), Wood.OAK, 1, 12, 0.05);
	public static final Axe COAL = new Axe("Coal Axe", 375L, new CraftingRecipe(Material.COAL, 10, Wood.OAK, 2), Material.COAL, 1, 10, 0.18);
	public static final Axe COPPER = new Axe("Copper Axe", 620L, new CraftingRecipe(Material.COPPER, 6, Wood.OAK, 4), Material.COPPER, 1, 25, 0.12);
	public static final Axe BRONZE = new Axe("Bronze Axe", 780L, new CraftingRecipe(Material.BRONZE, 7, Wood.CHERRYWOOD, 2), Material.BRONZE, 1, 28, 0.14);
	public static final Axe GOLD = new Axe("Gold Axe", 1200L, new CraftingRecipe(Material.GOLD, 2, Wood.CHERRYWOOD, 1), Material.GOLD, 2, 18, 0.17);
	public static final Axe IRON = new Axe("Iron Axe", 1750L, new CraftingRecipe(Material.IRON, 6, Wood.BIRCHWOOD, 2), Material.IRON, 1, 35, 0.22);
	public static final Axe TITANIUM = new Axe("Titanium Axe", 4200L, new CraftingRecipe(Material.TITANIUM, 3, Wood.BIRCHWOOD, 3), Material.TITANIUM, 2, 42, 0.3);
	public static final Axe URANIUM = new Axe("Uranium Axe", 9750L, new CraftingRecipe(Material.URANIUM, 1, Wood.KINGWOOD, 10), Material.URANIUM, 3, 32, 0.5);
	public static final Axe BITCOIN = new Axe("Bitcoin Axe", 18200L, new CraftingRecipe(Material.BITCOIN, 1, Wood.SNAKEWOOD, 1), Material.BITCOIN, 4, 45, 0.43);
	public static final Axe PLATINUM = new Axe("Platinum Axe", 32000L, new CraftingRecipe(Material.PLATINUM, 1), Material.PLATINUM, 5, 55, 0.54);
	public static final Axe SX4 = new Axe("Sx4 Axe", 63000L, null, null, 6, 60, 0.6);
	
	public static final Axe[] ALL = {WOOD, COAL, COPPER, BRONZE, GOLD, IRON, TITANIUM, URANIUM, BITCOIN, PLATINUM, SX4};
	
	private int maximumMaterials;
	private double multiplier;
	
	public Axe(String name, Long price, CraftingRecipe craft, Material repairItem, int maximumMaterials, int currentDurability, int durability, double multiplier, int upgrades) {
		super(name, price, craft, repairItem, currentDurability, durability, upgrades);
		
		this.maximumMaterials = maximumMaterials;
		this.multiplier = multiplier;
	}
	
	public Axe(String name, Long price, CraftingRecipe craft, Material repairItem, int maximumMaterials, int durability, double multiplier) {
		super(name, price, craft, repairItem, durability);
		
		this.maximumMaterials = maximumMaterials;
		this.multiplier = multiplier;
	}
	
	public int getMaximumMaterials() {
		return this.maximumMaterials;
	}
	
	public double getMultiplier() {
		return this.multiplier;
	}
	
	public Axe getDefaultAxe() {
		for (Axe axe : Axe.ALL) {
			if (axe.getName().equals(this.getName())) {
				return axe;
			}
		}
		
		return null;
	}
	
	public Map<String, Object> getStoreInfo() {
		Map<String, Object> axeInfo = new HashMap<>();
		axeInfo.put("upgrades", this.getUpgrades());
		axeInfo.put("durability", this.getDurability());
		axeInfo.put("price", this.getPrice());
		axeInfo.put("multiplier", this.multiplier);
		axeInfo.put("max_mats", this.maximumMaterials);
		
		return axeInfo;
	}
	
	public static Axe getAxeByName(String axeName) {
		axeName = axeName.toLowerCase();
		
		for (Axe axe : Axe.ALL) {
			if (axe.getName().toLowerCase().equals(axeName)) {
				return axe;
			}
		}
		
		for (Axe axe : Axe.ALL) {
			if (axe.getName().toLowerCase().startsWith(axeName)) {
				return axe;
			}
		}
		
		for (Axe axe : Axe.ALL) {
			if (axe.getName().toLowerCase().contains(axeName)) {
				return axe;
			}
		}
		
		return null;
	}
	
}
