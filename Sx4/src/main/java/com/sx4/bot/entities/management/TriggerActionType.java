package com.sx4.bot.entities.management;

public enum TriggerActionType {

	REQUEST(0, 2),
	SEND_MESSAGE(1, 1),
	ADD_REACTION(2, 1);

	private final int id, maxActions;

	private TriggerActionType(int id, int maxActions) {
		this.id = id;
		this.maxActions = maxActions;
	}

	public int getId() {
		return this.id;
	}

	public int getMaxActions() {
		return this.maxActions;
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
