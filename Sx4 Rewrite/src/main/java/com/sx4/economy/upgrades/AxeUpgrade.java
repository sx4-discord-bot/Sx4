package com.sx4.economy.upgrades;

public class AxeUpgrade extends Upgrade {

	public static final AxeUpgrade MULTIPLIER = new AxeUpgrade("Multiplier", "Multiplier upgrades increase your chance to get better materials per chop", 0.01);
	public static final AxeUpgrade DURABILITY = new AxeUpgrade("Durability", "Durability upgrades increase your max axe durability by 2", 2);
	
	public static final AxeUpgrade[] ALL = {MULTIPLIER, DURABILITY};
	
	public AxeUpgrade(String name, String description, double increasePerUpgrade) {
		super(name, description, increasePerUpgrade);
	}
	
	public static AxeUpgrade getAxeUpgradeByName(String upgradeName) {
		upgradeName = upgradeName.toLowerCase();
		for (AxeUpgrade upgrade : AxeUpgrade.ALL) {
			if (upgrade.getName().toLowerCase().equals(upgradeName)) {
				return upgrade;
			}
		}
		
		for (AxeUpgrade upgrade : AxeUpgrade.ALL) {
			if (upgrade.getName().toLowerCase().startsWith(upgradeName)) {
				return upgrade;
			}
		}
		
		for (AxeUpgrade upgrade : AxeUpgrade.ALL) {
			if (upgrade.getName().toLowerCase().contains(upgradeName)) {
				return upgrade;
			}
		}
		
		return null;
	}
	
}
