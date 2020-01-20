package com.sx4.bot.utils;

public enum ModAction {

	WARN(0, "Warn"),
	MUTE(1, "Mute"),
	MUTE_EXTEND(2, "Mute Extension"),
	KICK(3, "Kick"),
	BAN(4, "Ban");
	
	private final int type;
	private final String name;
	
	private ModAction(int type, String name) {
		this.type = type;
		this.name = name;
	}
	
	public int getType() {
		return this.type;
	}
	
	public String getName() {
		return this.name;
	}
	
	public static ModAction getFromType(int type) {
		for (ModAction action : ModAction.values()) {
			if (action.getType() == type) {
				return action;
			}
		}
		
		return null;
	}
	
}
