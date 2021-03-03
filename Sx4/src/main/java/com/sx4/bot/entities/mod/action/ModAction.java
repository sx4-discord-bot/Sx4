package com.sx4.bot.entities.mod.action;

import java.util.Arrays;

public enum ModAction {

	BAN("Ban", 0, true, false, false),
	TEMPORARY_BAN("Temporary Ban", 1, true, false, true),
	UNBAN("Unban", 2, false, false, false),
	KICK("Kick", 3, true, false, false),
	MUTE("Mute", 4, true, false, true),
	MUTE_EXTEND("Mute Extension", 5, true, true, true),
	UNMUTE("Unmute", 6, false, false, false),
	WARN("Warn", 7, true, false, false);
	
	private static final ModAction[] OFFENCES = Arrays.stream(ModAction.values()).filter(ModAction::isOffence).toArray(ModAction[]::new);
	
	private final String name;
	private final int type;
	private final boolean offence;
	private final boolean extend;
	private final boolean timed;
	
	private ModAction(String name, int type, boolean offence, boolean extend, boolean timed) {
		this.name = name;
		this.type = type;
		this.offence = offence;
		this.extend = extend;
		this.timed = timed;
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

	public boolean isTimed() {
		return this.timed;
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
