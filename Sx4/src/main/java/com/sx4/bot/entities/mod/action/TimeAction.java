package com.sx4.bot.entities.mod.action;

public class TimeAction extends Action {

	private final long duration;

	public TimeAction(ModAction action, long duration) {
		super(action);

		this.duration = duration;
	}
	
	public boolean hasDuration() {
		return this.duration != 0L;
	}
	
	public long getDuration() {
		return this.duration;
	}
	
}
