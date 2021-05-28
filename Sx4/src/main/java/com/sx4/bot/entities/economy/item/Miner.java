package com.sx4.bot.entities.economy.item;

import com.sx4.bot.managers.EconomyManager;
import org.bson.Document;

public class Miner extends Item {
	
	private final long maxMaterials;
	private final double multiplier;
	
	public Miner(Document data, Miner defaultMiner) {
		this(defaultMiner.getManager(), defaultMiner.getId(), defaultMiner.getName(), defaultMiner.getPrice(), defaultMiner.getMaxMaterials(), defaultMiner.getMultiplier());
	}

	public Miner(EconomyManager manager, int id, String name, long price, long maxMaterials, double multiplier) {
		super(manager, id, name, price, ItemType.MINER);
		
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
