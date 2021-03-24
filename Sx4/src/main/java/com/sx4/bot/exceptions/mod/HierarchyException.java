package com.sx4.bot.exceptions.mod;

public class HierarchyException extends ModException {

	private final String type;

	public HierarchyException(String type, String reason) {
		super(reason);
		this.type = type;
	}

	public String getType() {
		return this.type;
	}

}
