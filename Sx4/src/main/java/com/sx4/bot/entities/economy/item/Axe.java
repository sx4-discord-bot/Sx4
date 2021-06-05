package com.sx4.bot.entities.economy.item;

import com.sx4.bot.managers.EconomyManager;
import org.bson.Document;

import java.util.ArrayList;
import java.util.List;

public class Axe extends Tool {

	private final long maxMaterials;
	private final double multiplier;
	
	public Axe(Document data, Axe defaultAxe) {
		this(
			defaultAxe.getManager(),
			defaultAxe.getId(),
			defaultAxe.getName(), 
			data.get("price", defaultAxe.getPrice()),
			data.get("durability", defaultAxe.getDurability()),
			data.get("maxDurability", defaultAxe.getMaxDurability()),
			data.get("upgrades", defaultAxe.getUpgrades()),
			defaultAxe.getCraft(),
			defaultAxe.getRepairItem(),
			data.get("maxMaterials", defaultAxe.getMaxMaterials()),
			data.get("multiplier", defaultAxe.getMultiplier())
		);
	}

	public Axe(EconomyManager manager, int id, String name, long price, int durability, int maxDurability, int upgrades, List<ItemStack<CraftItem>> craft, CraftItem repairItem, long maxMaterials, double multiplier) {
		super(manager, id, name, price, ItemType.AXE, durability, maxDurability, upgrades, craft, repairItem);
		
		this.maxMaterials = maxMaterials;
		this.multiplier = multiplier;
	}
	
	public Axe(EconomyManager manager, int id, String name, long price, int maxDurability, int upgrades, List<ItemStack<CraftItem>> craft, CraftItem repairItem, long maxMaterials, double multiplier) {
		this(manager, id, name, price, maxDurability, maxDurability, upgrades, craft, repairItem, maxMaterials, multiplier);
	}

	public Axe(EconomyManager manager, int id, String name, long price, int maxDurability, List<ItemStack<CraftItem>> craft, CraftItem repairItem, long maxMaterials, double multiplier) {
		this(manager, id, name, price, maxDurability, maxDurability, 0, craft, repairItem, maxMaterials, multiplier);
	}
	
	public double getMultiplier() {
		return this.multiplier;
	}
	
	public long getMaxMaterials() {
		return this.maxMaterials;
	}

	public List<ItemStack<Wood>> getWoodYield() {
		List<ItemStack<Wood>> yield = new ArrayList<>();
		for (Wood wood : this.getManager().getItems(Wood.class)) {
			int amount = 0;
			for (int i = 0; i < this.maxMaterials; i++) {
				double randomDouble = this.getManager().getRandom().nextDouble();
				if (randomDouble <= Math.min(1, (1 / (wood.getPrice() / 45D)) * this.multiplier)) {
					amount++;
				}
			}

			if (amount != 0) {
				yield.add(new ItemStack<>(wood, amount));
			}
		}

		return yield;
	}

	public static Axe fromData(EconomyManager manager, Document data) {
		return new Axe(data, manager.getItemById(data.getInteger("id"), Axe.class));
	}
	
}
