package com.sx4.bot.entities.economy.item;

public enum ItemType {

	// Determines in which order items are loaded from the json file
	
	MATERIAL(0, "Material", "material", 500),
	WOOD(1, "Wood", "wood", 250),
	ROD(2, "Fishing Rod", "rod", 1),
	PICKAXE(3, "Pickaxe", "pickaxe", 1),
	AXE(4, "Axe", "axe", 1),
	MINER(5, "Miner", "miner", 10),
	FACTORY(6, "Factory", "factory", 10),
	CRATE(7, "Crate", "crate", 50),
	ENVELOPE(8, "Envelope", "envelope", 50),
	BOOSTER(9, "Booster", "booster", 50);
	
	private final int type;
	private final long defaultLimit;
	
	private final String name;
	private final String dataName;
	
	private ItemType(int type, String name, String dataName, long defaultLimit) {
		this.type = type;
		this.name = name;
		this.dataName = dataName;
		this.defaultLimit = defaultLimit;
	}
	
	public int getType() {
		return this.type;
	}
	
	public long getDefaultLimit() {
		return this.defaultLimit;
	}
	
	public String getName() {
		return this.name;
	}
	
	public String getDataName() {
		return this.dataName;
	}
	
	public static ItemType getFromType(int type) {
		for (ItemType itemType : ItemType.values()) {
			if (itemType.getType() == type) {
				return itemType;
			}
		}
		
		return null;
	}
	
	public static ItemType getFromName(String name) {
		for (ItemType itemType : ItemType.values()) {
			if (itemType.getName().equalsIgnoreCase(name)) {
				return itemType;
			}
		}
		
		return null;
	}
	
	public static ItemType getFromDataName(String dataName) {
		for (ItemType itemType : ItemType.values()) {
			if (itemType.getDataName().equals(dataName)) {
				return itemType;
			}
		}
		
		return null;
	}
	
}
