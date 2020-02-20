package com.sx4.bot.entities.economy;

import org.json.JSONObject;

public class Material extends Item {
	
	private final String emote;
	private final boolean hidden;
	
	public Material(JSONObject json) {
		this(json.getString("name"), json.getLong("price"), json.getString("emote"), json.getBoolean("hidden"));
	}

	public Material(String name, long price, String emote, boolean hidden) {
		super(name, price, ItemType.MATERIAL);
		
		this.emote = emote;
		this.hidden = hidden;
	}
	
	public String getEmote() {
		return this.emote;
	}
	
	public boolean isHidden() {
		return this.hidden;
	}
	
}
