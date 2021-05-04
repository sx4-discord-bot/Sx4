package com.sx4.bot.entities.economy.item;

import org.bson.Document;

public class Miner extends Item {
	
	private final long maxMaterials;
	private final double multiplier;
	
	public Miner(Document data, Miner defaultMiner) {
		this(defaultMiner.getId(), defaultMiner.getName(), defaultMiner.getPrice(), defaultMiner.getMaxMaterials(), defaultMiner.getMultiplier());
	}

	public Miner(int id, String name, long price, long maxMaterials, double multiplier) {
		super(id, name, price, ItemType.MINER);
		
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
