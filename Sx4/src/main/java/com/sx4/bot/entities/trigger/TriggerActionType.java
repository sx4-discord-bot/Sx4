package com.sx4.bot.entities.trigger;

import java.util.Arrays;
import java.util.EnumSet;

public enum TriggerActionType {

	REQUEST(0, 2),
	SEND_MESSAGE(1, 2),
	ADD_REACTION(2, 1),
	EXECUTE_COMMAND(3, 2),
	SEND_PAGED_MESSAGE(4, 2),
	REPLY_MESSAGE(5, 2, TriggerEventType.COMPONENT_CLICKED),
	EDIT_MESSAGE(6, 2, TriggerEventType.COMPONENT_CLICKED),
	DEFER_MESSAGE(7, 1, TriggerEventType.COMPONENT_CLICKED),
	PROXY(8, 1) {
		public EnumSet<TriggerEventType> getAllowedEvents() {
			return PROXY_EVENTS;
		}
	};

	private static final EnumSet<TriggerEventType> PROXY_EVENTS = EnumSet.allOf(TriggerEventType.class);
	static {
		PROXY_EVENTS.remove(TriggerEventType.PROXY_EXECUTED);
	}

	public static final int MAX_ACTIONS = 6;

	private final int id, maxActions;
	private final EnumSet<TriggerEventType> allowedEvents;

	private TriggerActionType(int id, int maxActions, TriggerEventType... allowedEvents) {
		this.id = id;
		this.maxActions = maxActions;
		this.allowedEvents = allowedEvents.length == 0 ? EnumSet.allOf(TriggerEventType.class) : EnumSet.copyOf(Arrays.asList(allowedEvents));
	}

	private TriggerActionType(int id, int maxActions, EnumSet<TriggerEventType> allowedEvents) {
		this.id = id;
		this.maxActions = maxActions;
		this.allowedEvents = allowedEvents;
	}

	public int getId() {
		return this.id;
	}

	public int getMaxActions() {
		return this.maxActions;
	}

	public EnumSet<TriggerEventType> getAllowedEvents() {
		return this.allowedEvents;
	}

	public boolean isAllowedEvent(TriggerEventType type) {
		return this.getAllowedEvents().contains(type);
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
