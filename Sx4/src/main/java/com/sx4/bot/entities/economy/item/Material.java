package com.sx4.bot.entities.economy.item;

import com.sx4.bot.managers.EconomyManager;
import org.bson.Document;

import java.time.LocalDate;

public class Material extends CraftItem {
	
	private final String emote;
	private final boolean hidden;
	private final int activeMonth;
	
	public Material(Document data, Material defaultMaterial) {
		this(defaultMaterial.getManager(), defaultMaterial.getId(), defaultMaterial.getName(), defaultMaterial.getPrice(), defaultMaterial.getEmote(), defaultMaterial.isHidden(), defaultMaterial.getActiveMonth());
	}

	public Material(EconomyManager manager, int id, String name, long price, ItemType type, String emote, boolean hidden, int activeMonth) {
		super(manager, id, name, price, type);
		
		this.emote = emote;
		this.hidden = hidden;
		this.activeMonth = activeMonth;
	}
	
	public Material(EconomyManager manager, int id, String name, long price, String emote, boolean hidden, int activeMonth) {
		this(manager, id, name, price, ItemType.MATERIAL, emote, hidden, activeMonth);
	}
	
	public String getEmote() {
		return this.emote;
	}

	public int getActiveMonth() {
		return this.activeMonth;
	}
	
	public boolean isHidden() {
		return this.hidden;
	}

	public boolean isObtainable() {
		if (this.hidden) {
			return false;
		}

		return this.activeMonth == -1 || this.activeMonth == LocalDate.now().getMonthValue();
	}
	
}
