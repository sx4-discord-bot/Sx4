package com.sx4.economy.items;

import com.sx4.economy.Item;

public class Miner extends Item {

	public static final Miner STONE = new Miner("Stone Miner", 3000, 1, 10);
	public static final Miner COPPER = new Miner("Copper Miner", 4500, 2, 8);
	public static final Miner BRONZE = new Miner("Bronze Miner", 5000, 3, 6);
	public static final Miner GOLD = new Miner("Gold Miner", 7500, 7, 11);
	public static final Miner IRON = new Miner("Iron Miner", 15000, 4, 3);
	public static final Miner TITANIUM = new Miner("Titanium Miner", 30000, 5, 1.8);
	public static final Miner URANIUM = new Miner("Uranium Miner", 100000, 6, 1.2);
	public static final Miner BITCOIN = new Miner("Bitcoin Miner", 150000, 8, 1.2);
	public static final Miner PLATINUM = new Miner("Platinum Miner", 275000, 10, 1);
	
	public static final Miner[] ALL = {STONE, COPPER, BRONZE, GOLD, IRON, TITANIUM, URANIUM, BITCOIN, PLATINUM};
	
	private int maximumMaterials;
	private double multiplier;
	
	public Miner(String name, long price, int maximumMaterials, double multiplier) {
		super(name, price);
		
		this.maximumMaterials = maximumMaterials;
		this.multiplier = multiplier;
	}
	
	public int getMaximumMaterials() {
		return this.maximumMaterials;
	}
	
	public double getMultiplier() {
		return this.multiplier;
	}
	
	public static Miner getMinerByName(String minerName) {
		minerName = minerName.toLowerCase();
			
		for (Miner miner : Miner.ALL) {
			if (miner.getName().toLowerCase().equals(minerName)) {
				return miner;
			}
		}
			
		for (Miner miner : Miner.ALL) {
			if (miner.getName().toLowerCase().startsWith(minerName)) {
				return miner;
			}
		}
			
		for (Miner miner : Miner.ALL) {
			if (miner.getName().toLowerCase().contains(minerName)) {
				return miner;
			}
		}
			
		return null;
	}
	
}
