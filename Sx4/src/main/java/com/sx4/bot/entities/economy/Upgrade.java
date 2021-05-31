package com.sx4.bot.entities.economy;

import com.sx4.bot.entities.economy.item.ItemType;

public class Upgrade {

	private final int id;

	private final String name, description;
	private final ItemType type;

	private final double value;

	public Upgrade(int id, ItemType type, String name, String description, double value) {
		this.id = id;
		this.type = type;
		this.name = name;
		this.description = description;
		this.value = value;
	}

	public int getId() {
		return this.id;
	}

	public String getName() {
		return this.name;
	}

	public String getDescription() {
		return this.description;
	}

	public ItemType getType() {
		return this.type;
	}

	public double getValue() {
		return this.value;
	}

}
