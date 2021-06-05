package com.sx4.bot.entities.economy.upgrade;

import com.sx4.bot.entities.economy.item.ItemType;

import java.util.EnumSet;

public enum Upgrade {

	DURABILITY(0, "Durability", "Upgrades your tools max durability by 2", 2D, ItemType.AXE, ItemType.PICKAXE, ItemType.ROD),
	MONEY(1, "Money", "Upgrades the yield of money given by a tool by 5% of it's original minimum yield", 0.05D, ItemType.ROD, ItemType.PICKAXE),
	MULTIPLIER(2, "Multiplier", "Upgrades the multiplier of your tool by 2%", 0.02D, ItemType.PICKAXE, ItemType.AXE);

	private final int id;

	private final String name, description;
	private final ItemType[] types;

	private final double value;

	private Upgrade(int id, String name, String description, double value, ItemType... types) {
		this.id = id;
		this.types = types;
		this.name = name;
		this.description = description;
		this.value = value;
	}

	public int getId() {
		return this.id;
	}

	public String getName() {
		return this.name;
	}

	public String getDescription() {
		return this.description;
	}

	public ItemType[] getTypes() {
		return this.types;
	}

	public double getValue() {
		return this.value;
	}

	public static Upgrade fromName(String name) {
		for (Upgrade upgrade : Upgrade.values()) {
			if (upgrade.getName().equalsIgnoreCase("name")) {
				return upgrade;
			}
		}

		return null;
	}

	public static EnumSet<Upgrade> getUpgrades(ItemType type) {
		EnumSet<Upgrade> upgrades = EnumSet.noneOf(Upgrade.class);
		for (Upgrade upgrade : Upgrade.values()) {
			for (ItemType itemType : upgrade.getTypes()) {
				if (itemType == type) {
					upgrades.add(upgrade);
					break;
				}
			}
		}

		return upgrades;
	}

}
