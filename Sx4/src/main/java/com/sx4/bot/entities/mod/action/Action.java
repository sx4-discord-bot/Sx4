package com.sx4.bot.entities.mod.action;

import com.sx4.bot.hooks.mod.ModAction;

public class Action {
	
	private final ModAction action;

	public Action(ModAction action) {
		this.action = action;
	}
	
	public ModAction getModAction() {
		return this.action;
	}
	
}
