package com.sx4.bot.entities.mod.action;

public class WarnAction extends Action {

	private final Warn warning;
	
	public WarnAction(Warn warning) {
		super(ModAction.WARN);
		
		this.warning = warning;
	}
	
	public Warn getWarning() {
		return this.warning;
	}
	
}
