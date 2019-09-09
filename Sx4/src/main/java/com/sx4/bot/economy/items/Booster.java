package com.sx4.bot.economy.items;

import com.sx4.bot.economy.Item;

public class Booster extends Item {
	
	public static final Booster LENDED_PICKAXE = new Booster("Lended Pickaxe", 2000, "Removes the cooldown on your pickaxe", true); 
	
	public static final Booster[] ALL = {LENDED_PICKAXE};
	
	private String description;
	private boolean activatable;
	
	public Booster(String name, long price, String description, boolean activatable) {
		super(name, price);
		
		this.description = description;
		this.activatable = activatable;
	}
	
	public String getDescription() {
		return this.description;
	}
	
	public boolean isActivatable() {
		return this.activatable;
	}
	
	public static Booster getBoosterByName(String boosterName) {
		boosterName = boosterName.toLowerCase();
			
		for (Booster booster : Booster.ALL) {
			if (booster.getName().toLowerCase().equals(boosterName)) {
				return booster;
			}
		}
			
		for (Booster booster : Booster.ALL) {
			if (booster.getName().toLowerCase().startsWith(boosterName)) {
				return booster;
			}
		}
			
		for (Booster booster : Booster.ALL) {
			if (booster.getName().toLowerCase().contains(boosterName)) {
				return booster;
			}
		}
			
		return null;
	}
	
}	
