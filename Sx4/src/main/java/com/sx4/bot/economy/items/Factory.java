package com.sx4.bot.economy.items;

import com.sx4.bot.economy.Item;
import com.sx4.bot.economy.materials.Material;

public class Factory extends Item { 

	public static final Factory SHOE = new Factory("Shoe Factory", Material.SHOE, 400, 6, 16);
	public static final Factory COAL = new Factory("Coal Factory", Material.COAL, 200, 25, 45);
	public static final Factory COPPER = new Factory("Copper Factory", Material.COPPER, 175, 25, 50);
	public static final Factory BRONZE = new Factory("Bronze Factory", Material.BRONZE, 125, 35, 70);
	public static final Factory IRON = new Factory("Iron Factory", Material.IRON, 100, 100, 130);
	public static final Factory ALUMINIUM = new Factory("Aluminium Factory", Material.ALUMINIUM, 85, 115, 180);
	public static final Factory OIL = new Factory("Oil Factory", Material.OIL, 75, 150, 220);
	public static final Factory GOLD = new Factory("Gold Factory", Material.GOLD, 40, 180, 236);
	public static final Factory SNOWFLAKE = new Factory("Snowflake Factory", Material.SNOWFLAKE, 20, 120, 300);
	public static final Factory PUMPKIN = new Factory("Pumpkin Factory", Material.PUMPKIN, 20, 120, 300);
	public static final Factory TITANIUM = new Factory("Titanium Factory", Material.TITANIUM, 20, 170, 236);
	public static final Factory URANIUM = new Factory("Uranium Factory", Material.URANIUM, 15, 400, 500);
	public static final Factory BITCOIN = new Factory("Bitcoin Factory", Material.BITCOIN, 10, 1250, 1700);
	public static final Factory PLATINUM = new Factory("Platinum Factory", Material.PLATINUM, 8, 3000, 5250);
	public static final Factory DIAMOND = new Factory("Diamond Factory", Material.DIAMOND, 5, 5000, 20000);
	
	public static final Factory[] ALL = {SHOE, COAL, COPPER, BRONZE, IRON, ALUMINIUM, OIL, GOLD, SNOWFLAKE, PUMPKIN, TITANIUM, URANIUM, BITCOIN, PLATINUM, DIAMOND}; 
	
	private Material material;
	private int amount;
	private int minimumYield;
	private int maximumYield;
		
	public Factory(String name, Material material, int amount, int minimumYield, int maximumYield) {
		super(name, material.getPrice() * amount);
		
		this.material = material;
		this.amount = amount;
		this.minimumYield = minimumYield;
		this.maximumYield = maximumYield;
	}
	
	public Material getMaterial() {
		return this.material;
	}
	
	public int getMaterialAmount() {
		return this.amount;
	}
	
	public int getMinimumYield() {
		return this.minimumYield;
	}
	
	public int getMaximumYield() {
		return this.maximumYield;
	}
	
	public boolean isHidden() {
		return this.material.isHidden();
	}
		
	public static Factory getFactoryByName(String factoryName) {
		factoryName = factoryName.toLowerCase();
			
		for (Factory factory : Factory.ALL) {
			if (factory.getName().toLowerCase().equals(factoryName)) {
				return factory;
			}
		}
			
		for (Factory factory : Factory.ALL) {
			if (factory.getName().toLowerCase().startsWith(factoryName)) {
				return factory;
			}
		}
			
		for (Factory factory : Factory.ALL) {
			if (factory.getName().toLowerCase().contains(factoryName)) {
				return factory;
			}
		}
			
		return null;
	}
	
}
