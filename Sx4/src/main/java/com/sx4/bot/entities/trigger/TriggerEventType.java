package com.sx4.bot.entities.trigger;

public enum TriggerEventType {

	MESSAGE_MATCHED(0),
	BUTTON_CLICKED(1);

	private final int id;

	private TriggerEventType(int id) {
		this.id = id;
	}

	public int getId() {
		return this.id;
	}

	public static TriggerEventType fromId(int id) {
		for (TriggerEventType event : TriggerEventType.values()) {
			if (event.getId() == id) {
				return event;
			}
		}

		return null;
	}

}
