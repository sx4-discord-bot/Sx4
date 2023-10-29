package com.sx4.bot.entities.interaction;

import java.util.function.Function;

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
	TRIGGER_VARIABLE_PURGE_CONFIRM(22),
	SHIP_SWIPE_RIGHT(23),
	TRIGGER_UPDATE_CONFIRM(24),
	TRIGGER_UPDATE_VIEW(25),
	TRIGGER_BUTTON_CLICKED(26);

	private final int id;
	private final Function<String, ? extends CustomButtonId> mapping;

	private ButtonType(int id) {
		this.id = id;
		this.mapping = CustomButtonId::fromId;
	}

	// TODO: Create a GenericButtonId which can be extended allowing for custom string ids
	private ButtonType(int id, Function<String, ? extends CustomButtonId> mapping) {
		this.id = id;
		this.mapping = mapping;
	}

	public int getId() {
		return this.id;
	}

	public CustomButtonId applyMapping(String id) {
		return this.mapping.apply(id);
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
