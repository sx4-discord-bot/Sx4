package com.sx4.bot.entities.mod;

import com.sx4.bot.entities.warn.WarnConfig;
import com.sx4.bot.hooks.mod.ModAction;

public class WarnAction extends Action {

	private final WarnConfig warning;
	
	public WarnAction(WarnConfig warning) {
		super(ModAction.WARN);
		
		this.warning = warning;
	}
	
	public WarnConfig getWarning() {
		return this.warning;
	}
	
}
