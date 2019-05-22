package com.sx4.economy.items;

import com.sx4.economy.Item;

public class Booster extends Item {
	
	public static final Booster LENDED_PICKAXE = new Booster("Lended Pickaxe", 2000, "Removes the cooldown on your pickaxe", true); 
	public static final Booster TAX_AVOIDER = new Booster("Tax Avoider", 3000, "Avoids tax when transferring money to another user", false);
	
	public static final Booster[] ALL = {LENDED_PICKAXE, TAX_AVOIDER};
	
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
