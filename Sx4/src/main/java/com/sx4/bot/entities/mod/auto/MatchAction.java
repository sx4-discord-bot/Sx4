package com.sx4.bot.entities.mod.auto;

public enum MatchAction {

	DELETE_MESSAGE(0),
	SEND_MESSAGE(1);

	public static final long ALL = MatchAction.getRaw(MatchAction.values());

	private final long raw;
	
	private MatchAction(int offset) {
		this.raw = 1L << offset;
	}
	
	public long getRaw() {
		return this.raw;
	}

	public static long getRaw(MatchAction... actions) {
		long raw = 0;
		for (MatchAction action : actions) {
			raw |= action.getRaw();
		}

		return raw;
	}
	
}
