package com.sx4.bot.entities.interaction;

public enum ButtonType {

	MARRIAGE_CONFIRM(0),
	MARRIAGE_REJECT(1),
	DIVORCE_ALL_CONFIRM(2),
	GENERIC_REJECT(3),
	GUESS_THE_NUMBER_CONFIRM(4),
	GUESS_THE_NUMBER_MODAL(5),
	GIVEAWAY_DELETE_CONFIRM(6),
	TEMPLATE_DELETE_CONFIRM(7),
	SELF_ROLE_DELETE_CONFIRM(8),
	FISHING_ROD_REPAIR_CONFIRM(9),
	PICKAXE_REPAIR_CONFIRM(10),
	AXE_REPAIR_CONFIRM(11),
	AUTO_ROLE_DELETE_CONFIRM(12),
	PREMIUM_CONFIRM(13),
	FAKE_PERMISSIONS_DELETE_CONFIRM(14),
	TRIGGER_DELETE_CONFIRM(15),
	SUGGESTION_DELETE_CONFIRM(16),
	STARBOARD_DELETE_CONFIRM(17),
	REACTION_ROLE_DELETE_CONFIRM(18),
	MOD_LOG_DELETE_CONFIRM(19),
	CHANNEL_DELETE_CONFIRM(20),
	SHIP_SWIPE_LEFT(21),
	TRIGGER_VARIABLE_PURGE_CONFIRM(22);

	private final int id;

	private ButtonType(int id) {
		this.id = id;
	}

	public int getId() {
		return this.id;
	}

	public static ButtonType fromId(int id) {
		for (ButtonType type : ButtonType.values()) {
			if (type.getId() == id) {
				return type;
			}
		}

		return null;
	}

}
