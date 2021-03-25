package com.sx4.bot.entities.mod.auto;

import com.sx4.bot.managers.AntiRegexManager;

public enum RegexType {

	REGEX(0, AntiRegexManager.DEFAULT_MOD_MESSAGE, AntiRegexManager.DEFAULT_MATCH_MESSAGE),
	INVITE(1, AntiRegexManager.DEFAULT_INVITE_MOD_MESSAGE, AntiRegexManager.DEFAULT_INVITE_MATCH_MESSAGE);

	private final int id;

	private final String modMessage;
	private final String matchMessage;

	private RegexType(int id, String modMessage, String matchMessage) {
		this.id = id;
		this.modMessage = modMessage;
		this.matchMessage = matchMessage;
	}

	public String getDefaultModMessage() {
		return this.modMessage;
	}

	public String getDefaultMatchMessage() {
		return this.matchMessage;
	}

	public int getId() {
		return this.id;
	}

	public static RegexType fromId(int id) {
		for (RegexType type : RegexType.values()) {
			if (type.getId() == id) {
				return type;
			}
		}

		return null;
	}

}
