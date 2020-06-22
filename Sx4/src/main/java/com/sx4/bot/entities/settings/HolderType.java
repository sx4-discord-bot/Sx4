package com.sx4.bot.entities.settings;

public enum HolderType {

	USER(0),
	ROLE(1);
	
	private final int type;
	
	private HolderType(int type) {
		this.type = type;
	}
	
	public int getType() {
		return this.type;
	}
	
	public static HolderType fromType(int type) {
		for (HolderType holderType : HolderType.values()) {
			if (holderType.getType() == type) {
				return holderType;
			}
		}
		
		return null;
	}
	
}
