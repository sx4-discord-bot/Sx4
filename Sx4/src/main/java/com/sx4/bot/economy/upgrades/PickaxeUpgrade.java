package com.sx4.bot.economy.upgrades;

public class PickaxeUpgrade extends Upgrade {
	
	public static final PickaxeUpgrade MONEY = new PickaxeUpgrade("Money", "Money upgrades increase your minimum and maximum yield of money from your pickaxe by 5% the worth of the original minimum yield", 0.05); 
	public static final PickaxeUpgrade MULTIPLIER = new PickaxeUpgrade("Multiplier", "Multiplier upgrades increase your chance to get better materials per mine", 1.02);
	public static final PickaxeUpgrade DURABILITY = new PickaxeUpgrade("Durability", "Durability upgrades increase your max durability by 2", 2);
	
	public static final PickaxeUpgrade[] ALL = {MONEY, MULTIPLIER, DURABILITY};

	public PickaxeUpgrade(String name, String description, double increasePerUpgrade) {
		super(name, description, increasePerUpgrade);
	}
	
	public static PickaxeUpgrade getPickaxeUpgradeByName(String upgradeName) {
		upgradeName = upgradeName.toLowerCase();
		for (PickaxeUpgrade upgrade : PickaxeUpgrade.ALL) {
			if (upgrade.getName().toLowerCase().equals(upgradeName)) {
				return upgrade;
			}
		}
		
		for (PickaxeUpgrade upgrade : PickaxeUpgrade.ALL) {
			if (upgrade.getName().toLowerCase().startsWith(upgradeName)) {
				return upgrade;
			}
		}
		
		for (PickaxeUpgrade upgrade : PickaxeUpgrade.ALL) {
			if (upgrade.getName().toLowerCase().contains(upgradeName)) {
				return upgrade;
			}
		}
		
		return null;
	}
}
