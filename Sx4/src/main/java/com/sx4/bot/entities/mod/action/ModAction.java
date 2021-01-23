package com.sx4.bot.entities.mod.action;

import java.util.Arrays;

public enum ModAction {

	BAN("Ban", 0, true, false),
	TEMPORARY_BAN("Temporary Ban", 1, true, false),
	UNBAN("Unban", 2, false, false),
	KICK("Kick", 3, true, false),
	MUTE("Mute", 4, true, false),
	MUTE_EXTEND("Mute Extension", 5, true, true),
	UNMUTE("Unmute", 6, false, false),
	WARN("Warn", 7, true, false);
	
	private static final ModAction[] OFFENCES = Arrays.stream(ModAction.values()).filter(ModAction::isOffence).toArray(ModAction[]::new);
	
	private final String name;
	private final int type;
	private final boolean offence;
	private final boolean extend;
	
	private ModAction(String name, int type, boolean offence, boolean extend) {
		this.name = name;
		this.type = type;
		this.offence = offence;
		this.extend = extend;
	}
	
	public String getName() {
		return this.name;
	}
	
	public int getType() {
		return this.type;
	}
	
	public boolean isOffence() {
		return this.offence;
	}

	public boolean isExtend() {
		return this.extend;
	}
	
	public static ModAction fromType(int type) {
		for (ModAction action : ModAction.values()) {
			if (action.getType() == type) {
				return action;
			}
		}
		
		return null;
	}
	
	public static ModAction[] getOffences() {
		return ModAction.OFFENCES;
	}
	
}
