package com.sx4.bot.entities.economy;

public enum ItemType {

	ROD(0, "Fishing Rod", "rod", 1),
	PICKAXE(1, "Pickaxe", "pick", 1),
	AXE(2, "Axe", "axe", 1),
	MATERIAL(3, "Material", "mat", 750),
	MINER(4, "Miner", "miner", 10),
	FACTORY(5, "Factory", "factory", 10),
	CRATE(6, "Crate", "crate", 50),
	ENVELOPE(7, "Envelope", "env", 50),
	BOOSTER(8, "Booster", "booster", 50);
	
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
			if (itemType.getDataName().equals(name)) {
				return itemType;
			}
		}
		
		return null;
	}
	
}
