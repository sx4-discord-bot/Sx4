package com.sx4.bot.entities.management;

public enum TriggerActionType {

	REQUEST(0);

	private final int id;

	private TriggerActionType(int id) {
		this.id = id;
	}

	public int getId() {
		return this.id;
	}

	public static TriggerActionType fromId(int id) {
		for (TriggerActionType action : TriggerActionType.values()) {
			if (action.getId() == id) {
				return action;
			}
		}

		return null;
	}

}
