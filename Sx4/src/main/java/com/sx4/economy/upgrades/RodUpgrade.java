package com.sx4.economy.upgrades;

public class RodUpgrade extends Upgrade {

	public static final RodUpgrade MONEY = new RodUpgrade("Money", "Money upgrades increase your minimum yield and maximum yield of money from your fishing rod by 2% the worth of the original minimum yield", 0.02);
	public static final RodUpgrade DURABILITY = new RodUpgrade("Durability", "Durability upgrades increase your max fishing rod durability by 2", 2);
	
	public static final RodUpgrade[] ALL = {MONEY, DURABILITY};
	
	public RodUpgrade(String name, String description, double increasePerUpgrade) {
		super(name, description, increasePerUpgrade);
	}
	
	public static RodUpgrade getRodUpgradeByName(String upgradeName) {
		upgradeName = upgradeName.toLowerCase();
		for (RodUpgrade upgrade : RodUpgrade.ALL) {
			if (upgrade.getName().toLowerCase().equals(upgradeName)) {
				return upgrade;
			}
		}
		
		for (RodUpgrade upgrade : RodUpgrade.ALL) {
			if (upgrade.getName().toLowerCase().startsWith(upgradeName)) {
				return upgrade;
			}
		}
		
		for (RodUpgrade upgrade : RodUpgrade.ALL) {
			if (upgrade.getName().toLowerCase().contains(upgradeName)) {
				return upgrade;
			}
		}
		
		return null;
	}
	
}
