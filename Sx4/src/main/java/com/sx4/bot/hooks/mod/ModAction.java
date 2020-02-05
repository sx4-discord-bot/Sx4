package com.sx4.bot.hooks.mod;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public enum ModAction {

	BAN("Ban", 0, true),
	TEMP_BAN("Temporary Ban", 1, true),
	UNBAN("Unban", 2, false),
	KICK("Kick", 3, true),
	MUTE("Mute", 4, true),
	MUTE_EXTEND("Mute Extension", 5, true),
	UNMUTE("Unmute", 6, false),
	WARN("Warn", 7, true);
	
	private static final Set<ModAction> OFFENCES = Arrays.stream(ModAction.values()).filter(ModAction::isOffence).collect(Collectors.toSet());
	
	private final String name;
	private final int type;
	private final boolean offence;
	
	private ModAction(String name, int type, boolean offence) {
		this.name = name;
		this.type = type;
		this.offence = offence;
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
	
	public static ModAction getFromType(int type) {
		for (ModAction action : ModAction.values()) {
			if (action.getType() == type) {
				return action;
			}
		}
		
		return null;
	}
	
	public static Set<ModAction> getOffences() {
		return ModAction.OFFENCES;
	}
	
}
