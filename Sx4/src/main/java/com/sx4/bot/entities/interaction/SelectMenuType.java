package com.sx4.bot.entities.interaction;

public enum SelectMenuType {

	SUB_COMMAND_SELECT(0);

	private final int id;

	private SelectMenuType(int id) {
		this.id = id;
	}

	public int getId() {
		return this.id;
	}

	public static SelectMenuType fromId(int id) {
		for (SelectMenuType type : SelectMenuType.values()) {
			if (type.getId() == id) {
				return type;
			}
		}

		return null;
	}

}
