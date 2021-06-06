package com.sx4.bot.entities.economy.item;

import com.sx4.bot.managers.EconomyManager;
import org.bson.Document;

import java.util.ArrayList;
import java.util.List;

public class Pickaxe extends Tool {

	private final long minYield;
	private final long maxYield;
	
	private final double multiplier;

	public Pickaxe(Document data, Pickaxe defaultPickaxe) {
		this(
			defaultPickaxe.getManager(),
			defaultPickaxe.getId(),
			defaultPickaxe.getName(),
			data.get("price", defaultPickaxe.getPrice()),
			data.get("durability", defaultPickaxe.getDurability()),
			data.get("maxDurability", defaultPickaxe.getMaxDurability()),
			data.get("upgrades", defaultPickaxe.getUpgrades()),
			defaultPickaxe.getCraft(),
			defaultPickaxe.getRepairItem(),
			data.get("minYield", defaultPickaxe.getMinYield()),
			data.get("maxYield", defaultPickaxe.getMaxYield()),
			data.get("multiplier", defaultPickaxe.getMultiplier())
		);
	}

	public Pickaxe(EconomyManager manager, int id, String name, long price, int durability, int maxDurability, int upgrades, List<ItemStack<CraftItem>> craft, CraftItem repairItem, long minYield, long maxYield, double multiplier) {
		super(manager, id, name, price, ItemType.PICKAXE, durability, maxDurability, upgrades, craft, repairItem);
		
		this.minYield = minYield;
		this.maxYield = maxYield;
		this.multiplier = multiplier;
	}
	
	public Pickaxe(EconomyManager manager, int id, String name, long price, int maxDurability, int upgrades, List<ItemStack<CraftItem>> craft, CraftItem repairItem, long minYield, long maxYield, double multiplier) {
		this(manager, id, name, price, maxDurability, maxDurability, upgrades, craft, repairItem, minYield, maxYield, multiplier);
	}

	public Pickaxe(EconomyManager manager, int id, String name, long price, int maxDurability, List<ItemStack<CraftItem>> craft, CraftItem repairItem, long minYield, long maxYield, double multiplier) {
		this(manager, id, name, price, maxDurability, maxDurability, 0, craft, repairItem, minYield, maxYield, multiplier);
	}
	
	public double getMultiplier() {
		return this.multiplier;
	}

	public List<ItemStack<Material>> getMaterialYield() {
		List<ItemStack<Material>> materials = new ArrayList<>();
		for (Material material : this.getManager().getItems(Material.class)) {
			if (material.isHidden()) {
				continue;
			}

			double randomDouble = this.getManager().getRandom().nextDouble();
			if (randomDouble <= Math.min(1, (1 / (material.getPrice() / 10D)) * this.multiplier)) {
				materials.add(new ItemStack<>(material, 1));
			}
		}

		return materials;
	}

	public long getYield() {
		return this.getManager().getRandom().nextInt((int) (this.maxYield - this.minYield) + 1) + this.minYield;
	}
	
	public long getMinYield() {
		return this.minYield;
	}
	
	public long getMaxYield() {
		return this.maxYield;
	}

	public static Pickaxe fromData(EconomyManager manager, Document data) {
		return new Pickaxe(data, manager.getItemById(data.getInteger("id"), Pickaxe.class));
	}

	public Document toData() {
		return super.toData()
			.append("multiplier", this.multiplier)
			.append("minYield", this.minYield)
			.append("maxYield", this.maxYield);
	}
	
}
