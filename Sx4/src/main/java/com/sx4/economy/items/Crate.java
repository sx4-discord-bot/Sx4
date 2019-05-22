package com.sx4.economy.items;

import com.sx4.economy.Item;

public class Crate extends Item {

	public static final Crate PRESENT = new Crate("Present Crate", null, 5200D, false);
	public static final Crate SHOE = new Crate("Shoe Crate", 175L, true);
	public static final Crate COAL = new Crate("Coal Crate", 340L, true);
	public static final Crate COPPER = new Crate("Copper Crate", 600L, true);
	public static final Crate BRONZE = new Crate("Bronze Crate", 1750L, true);
	public static final Crate GOLD = new Crate("Gold Crate", 2500L, true);
	public static final Crate IRON = new Crate("Iron Crate", 6700L, true);
	public static final Crate OIL = new Crate("Oil Crate", 12000L, true);
	public static final Crate TITANIUM = new Crate("Titanium Crate", 22500L, true);
	public static final Crate URANIUM = new Crate("Uranium Crate", 56250L, true);
	public static final Crate BITCOIN = new Crate("Bitcoin Crate", 185000L, true);
	public static final Crate PLATINUM = new Crate("Platinum Crate", 300000L, true);
	public static final Crate DIAMOND = new Crate("Diamond Crate", 460000L, true);
	
	public static final Crate[] ALL = {PRESENT, SHOE, COAL, COPPER, BRONZE, GOLD, IRON, OIL, TITANIUM, URANIUM, BITCOIN, PLATINUM, DIAMOND};
	
	private boolean openable;
	private double chance = 0;
	
	public Crate(String name, Long price, boolean openable) {
		super(name, price);
		
		this.openable = openable;
	}
	
	public Crate(String name, Long price, double chance, boolean openable) {
		super(name, price);
		
		this.chance = chance;
		this.openable = openable;
	}
	
	public boolean isOpenable() {
		return this.openable;
	}
	
	public double getChance() {
		return this.chance == 0 ? this.getPrice() / 10 : this.chance;
	}
	
	public static Crate getCrateByName(String crateName) {
		crateName = crateName.toLowerCase();
			
		for (Crate crate : Crate.ALL) {
			if (crate.getName().toLowerCase().equals(crateName)) {
				return crate;
			}
		}
			
		for (Crate crate : Crate.ALL) {
			if (crate.getName().toLowerCase().startsWith(crateName)) {
				return crate;
			}
		}
			
		for (Crate crate : Crate.ALL) {
			if (crate.getName().toLowerCase().contains(crateName)) {
				return crate;
			}
		}
			
		return null;
	}
	
}
