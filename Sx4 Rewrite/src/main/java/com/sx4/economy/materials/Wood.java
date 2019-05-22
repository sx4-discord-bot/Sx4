package com.sx4.economy.materials;

public class Wood extends Material {
	
	public static final Wood OAK = new Wood("Oak", 75);
	public static final Wood CHERRYWOOD = new Wood("Cherrywood", 175);
	public static final Wood BIRCHWOOD = new Wood("Birchwood", 300);
	public static final Wood KINGWOOD = new Wood("Kingwood", 375);
	public static final Wood SNAKEWOOD = new Wood("Snakewood", 500);
	
	public static final Wood[] ALL = {OAK, CHERRYWOOD, BIRCHWOOD, KINGWOOD, SNAKEWOOD};
	
	private int multiplier = 45; 
	
	public Wood(String name, int price) {
		super(name, price, null, false);
	}
	
	public int getChance() {
		return (int) Math.ceil((double) this.getPrice() / this.multiplier);
	}
	
	public static Wood getWoodByName(String woodName) {
		woodName = woodName.toLowerCase();
			
		for (Wood wood : Wood.ALL) {
			if (wood.getName().toLowerCase().equals(woodName)) {
				return wood;
			}
		}
			
		for (Wood wood : Wood.ALL) {
			if (wood.getName().toLowerCase().startsWith(woodName)) {
				return wood;
			}
		}
			
		for (Wood wood : Wood.ALL) {
			if (wood.getName().toLowerCase().contains(woodName)) {
				return wood;
			}
		}
			
		return null;
	}
}
