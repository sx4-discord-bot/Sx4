package com.sx4.bot.entities.economy.item;

public class Miner extends Item {
	
	private final long maxMaterials;
	private final double multiplier;

	public Miner(String name, long price, long maxMaterials, double multiplier) {
		super(name, price, ItemType.MINER);
		
		this.maxMaterials = maxMaterials;
		this.multiplier = multiplier;
	}
	
	public long getMaxMaterials() {
		return this.maxMaterials;
	}
	
	public double getMultiplier() {
		return this.multiplier;
	}
	
}
