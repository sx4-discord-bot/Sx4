package com.sx4.economy.materials;

import com.sx4.economy.Item;

public class Material extends Item {

	public static final Material SHOE = new Material("Shoe", 2, ":athletic_shoe:", false);
	public static final Material COAL = new Material("Coal", 20, "<:coal:441006067523256350>", false);
	public static final Material COPPER = new Material("Copper", 50, "<:copper:441006065828757504>", false);
	public static final Material BRONZE = new Material("Bronze", 80, "<:bronze:441006064381460494>", false);
	public static final Material IRON = new Material("Iron", 125, "<:iron:441006065069326357>", false);
	public static final Material ALUMINIUM = new Material("Aluminium", 200, "<:aluminium:441006064545300491>", false);
	public static final Material OIL = new Material("Oil", 300, "<:oil:441006064243179531>", false);
	public static final Material GOLD = new Material("Gold", 500, "<:gold:441006068328300551>", false);
	public static final Material SNOWFLAKE = new Material("Snowflake", 750, ":snowflake:", true);
	public static final Material PUMPKIN = new Material("Pumpkin", 750, ":jack_o_lantern:", true);
	public static final Material TITANIUM = new Material("Titanium", 1000, "<:titanium:441006065639751683>", false);
	public static final Material URANIUM = new Material("Uranium", 5500, "<:uranium:441006059348295680>", false);
	public static final Material BITCOIN = new Material("Bitcoin", 17500, "<:bitcoin:441006066273353750>", false);
	public static final Material PLATINUM = new Material("Platinum", 75000, "<:platinum:441006059008688139>", false);
	public static final Material DIAMOND = new Material("Diamond", 500000, "<:diamond:441251890186158081>", false);
	
	public static final Material[] ALL = {SHOE, COAL, COPPER, BRONZE, IRON, ALUMINIUM, OIL, GOLD, SNOWFLAKE, PUMPKIN, TITANIUM, URANIUM, BITCOIN, PLATINUM, DIAMOND};

	private String emote;
	private boolean hidden;
	private int multiplier = 12; 
		
	public Material(String name, long price, String emote, boolean hidden) {
		super(name, price);
		
		this.emote = emote;
		this.hidden = hidden;
	}
		
	public boolean isHidden() {
		return this.hidden;
	}
		
	public String getEmote() {
		return this.emote;
	}
		
	public int getChance() {
		return (int) Math.ceil((double) this.getPrice() / this.multiplier);
	}
		
	public static Material getMaterialByName(String materialName) {
		materialName = materialName.toLowerCase();
			
		for (Material material : Material.ALL) {
			if (material.getName().toLowerCase().equals(materialName)) {
				return material;
			}
		}
			
		for (Material material : Material.ALL) {
			if (material.getName().toLowerCase().startsWith(materialName)) {
				return material;
			}
		}
			
		for (Material material : Material.ALL) {
			if (material.getName().toLowerCase().contains(materialName)) {
				return material;
			}
		}
			
		return null;
	}
}
