package com.sx4.bot.entities.economy.item;

import com.sx4.bot.managers.EconomyManager;
import org.bson.Document;

public class Material extends CraftItem {
	
	private final String emote;
	private final boolean hidden;
	
	public Material(Document data, Material defaultMaterial) {
		this(defaultMaterial.getManager(), defaultMaterial.getId(), defaultMaterial.getName(), defaultMaterial.getPrice(), defaultMaterial.getEmote(), defaultMaterial.isHidden());
	}

	public Material(EconomyManager manager, int id, String name, long price, ItemType type, String emote, boolean hidden) {
		super(manager, id, name, price, type);
		
		this.emote = emote;
		this.hidden = hidden;
	}
	
	public Material(EconomyManager manager, int id, String name, long price, String emote, boolean hidden) {
		this(manager, id, name, price, ItemType.MATERIAL, emote, hidden);
	}
	
	public String getEmote() {
		return this.emote;
	}
	
	public boolean isHidden() {
		return this.hidden;
	}
	
}
