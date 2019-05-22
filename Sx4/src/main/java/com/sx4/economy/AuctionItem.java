package com.sx4.economy;

import java.util.Map;

import com.sx4.economy.tools.Axe;
import com.sx4.economy.tools.Pickaxe;
import com.sx4.economy.tools.Rod;
import com.sx4.economy.tools.Tool;
import com.sx4.utils.EconomyUtils;

import net.dv8tion.jda.bot.sharding.ShardManager;
import net.dv8tion.jda.core.entities.User;

public class AuctionItem {
	
	private Map<String, Object> itemData;
	private Item item;
	private long amount;
	private Long durability;
	private long price;
	private String ownerId;
	private String id;

	@SuppressWarnings("unchecked")
	public AuctionItem(Map<String, Object> auctionData) {
		this.itemData = (Map<String, Object>) auctionData.get("item");
		this.item = EconomyUtils.getItem((String) this.itemData.get("name"));
		this.amount = (long) itemData.get("amount");
		this.durability = (Long) auctionData.get("durability");
		this.ownerId = (String) auctionData.get("ownerid");
		this.id = (String) auctionData.get("id");
		
		Object priceObject = auctionData.get("price");
		if (priceObject instanceof Double) {
			this.price = (long) ((double) priceObject);
		} else {
			this.price = (long) priceObject;
		}
	}
	
	public String getId() {
		return this.id;
	}
	
	public Long getDurability() {
		return this.durability;
	}
	
	public String getOwnerId() {
		return this.ownerId;
	}
	
	public User getOwner(ShardManager shardManager) {
		return shardManager.getUserById(this.ownerId);
	}
	
	public Item getItem() {
		return this.item;
	}
	
	public long getAmount() {
		return this.amount;
	}
	
	public long getPrice() {
		return this.price;
	}
	
	public double getPricePerItem() {
		return (double) this.price / this.amount;
	}
	
	public boolean isTool() {
		return this.item instanceof Tool;
	}
	
	public Tool getTool() {
		Tool tool = (Tool) this.item;
		return new Tool(tool.getName(), 
				this.itemData.containsKey("price") ? Math.toIntExact((long) this.itemData.get("price")) : tool.getPrice(), 
				tool.getCraftingRecipe(), 
				tool.getRepairItem(), 
				Math.toIntExact(this.durability),
				this.itemData.containsKey("durability") ? Math.toIntExact((long) this.itemData.get("durability")) : tool.getDurability(), 
				this.itemData.containsKey("upgrades") ? Math.toIntExact((long) this.itemData.get("upgrades")) : tool.getUpgrades());
	}
	
	public boolean isPickaxe() {
		return this.item instanceof Pickaxe;
	}
	
	public Pickaxe getPickaxe() {
		Pickaxe pickaxe = (Pickaxe) this.item;
		return new Pickaxe(pickaxe.getName(), 
				this.itemData.containsKey("price") ? (long) this.itemData.get("price") : pickaxe.getPrice(), 
				pickaxe.getCraftingRecipe(), 
				pickaxe.getRepairItem(), 
				this.itemData.containsKey("rand_min") ? Math.toIntExact((long) this.itemData.get("rand_min")) : pickaxe.getMinimumYield(), 
				this.itemData.containsKey("rand_max") ? Math.toIntExact((long) this.itemData.get("rand_max")) : pickaxe.getMaximumYield(),
				Math.toIntExact(this.durability),
				this.itemData.containsKey("durability") ? Math.toIntExact((long) this.itemData.get("durability")) : pickaxe.getDurability(), 
				this.itemData.containsKey("multiplier") ? (double) this.itemData.get("multiplier") : pickaxe.getMultiplier(),
				this.itemData.containsKey("upgrades") ? Math.toIntExact((long) this.itemData.get("upgrades")) : pickaxe.getUpgrades());
	}
	
	public boolean isRod() {
		return this.item instanceof Rod;
	}
	
	public Rod getRod() {
		Rod rod = (Rod) this.item;
		return new Rod(rod.getName(), 
				this.itemData.containsKey("price") ? (long) this.itemData.get("price") : rod.getPrice(), 
				rod.getCraftingRecipe(), 
				rod.getRepairItem(), 
				this.itemData.containsKey("rand_min") ? Math.toIntExact((long) this.itemData.get("rand_min")) : rod.getMinimumYield(), 
				this.itemData.containsKey("rand_max") ? Math.toIntExact((long) this.itemData.get("rand_max")) : rod.getMaximumYield(),
				Math.toIntExact(this.durability),
				this.itemData.containsKey("durability") ? Math.toIntExact((long) this.itemData.get("durability")) : rod.getDurability(), 
				this.itemData.containsKey("upgrades") ? Math.toIntExact((long) this.itemData.get("upgrades")) : rod.getUpgrades());
	}
	
	public boolean isAxe() {
		return this.item instanceof Axe;
	}
	
	public Axe getAxe() {
		Axe axe = (Axe) this.item;
		return new Axe(axe.getName(), 
				this.itemData.containsKey("price") ? (long) this.itemData.get("price") : axe.getPrice(), 
				axe.getCraftingRecipe(), 
				axe.getRepairItem(), 
				this.itemData.containsKey("max_mats") ? Math.toIntExact((long) this.itemData.get("max_mats")) : axe.getMaximumMaterials(), 
				Math.toIntExact(this.durability),
				this.itemData.containsKey("durability") ? Math.toIntExact((long) this.itemData.get("durability")) : axe.getDurability(), 
				this.itemData.containsKey("multiplier") ? (double) this.itemData.get("multiplier") : axe.getMultiplier(),
				this.itemData.containsKey("upgrades") ? Math.toIntExact((long) this.itemData.get("upgrades")) : axe.getUpgrades());
	}
	
}
