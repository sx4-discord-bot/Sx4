package com.sx4.bot.entities.interaction;

public enum ModalType {

	GUESS_THE_NUMBER(0);

	private final int id;

	private ModalType(int id) {
		this.id = id;
	}

	public int getId() {
		return this.id;
	}

	public static ModalType fromId(int id) {
		for (ModalType type : ModalType.values()) {
			if (type.getId() == id) {
				return type;
			}
		}

		return null;
	}

}
