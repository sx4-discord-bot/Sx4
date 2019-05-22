package com.sx4.economy.upgrades;

public class Upgrade {

	private String name;
	private String description;
	private double increasePerUpgrade;
	
	public Upgrade(String name, String description, double increasePerUpgrade) {
		this.name = name;
		this.description = description;
		this.increasePerUpgrade = increasePerUpgrade;
	}
	
	public String toString() {
		return this.name;
	}
	
	public String getName() {
		return this.name;
	}
	
	public String getDescription() {
		return this.description;
	}
	
	public double getIncreasePerUpgrade() {
		return this.increasePerUpgrade;
	}
}
