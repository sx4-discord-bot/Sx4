package com.sx4.bot.entities.mod.auto;

public enum MatchAction {

	DELETE(0),
	SEND_MESSAGE(1);
	
	private final long raw;
	
	private MatchAction(int offset) {
		this.raw = 1L << offset;
	}
	
	public long getRaw() {
		return this.raw;
	}
	
}
