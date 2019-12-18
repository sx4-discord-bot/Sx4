package com.sx4.bot.economy;
	

import org.bson.Document;
import org.bson.types.ObjectId;

import com.sx4.bot.core.Sx4Bot;
import com.sx4.bot.economy.tools.Axe;
import com.sx4.bot.economy.tools.Pickaxe;
import com.sx4.bot.economy.tools.Rod;
import com.sx4.bot.economy.tools.Tool;

import net.dv8tion.jda.api.entities.User;

public class AuctionItem {
	
	private Document itemData;
	private Item item;
	private long price;
	private long ownerId;
	private ObjectId id;

	public AuctionItem(Document auctionData) {
		this.itemData = auctionData.get("item", Document.class);
		this.item = Item.getItemByName(this.itemData.getString("name"));
		this.ownerId = auctionData.getLong("ownerId");
		this.id = auctionData.getObjectId("_id");
		this.price = auctionData.getLong("price");
	}
	
	public ObjectId getId() {
		return this.id;
	}
	
	public long getOwnerId() {
		return this.ownerId;
	}
	
	public User getOwner() {
		return Sx4Bot.getShardManager().getUserById(this.ownerId);
	}
	
	public Item getItem() {
		return this.item;
	}
	
	public long getAmount() {
		return this.itemData.getLong("amount");
	}
	
	public long getPrice() {
		return this.price;
	}
	
	public double getPricePerItem() {
		return (double) this.price / this.getAmount();
	}
	
	public ItemStack<Item> getItemStack() {
		return new ItemStack<>(this.item, this.getAmount());
	}
	
	public boolean isTool() {
		return this.item instanceof Tool;
	}
	
	public Tool getTool() {
		Tool tool = (Tool) this.item;
		return new Tool(
				tool.getName(), 
				this.itemData.get("price", tool.getPrice()), 
				tool.getCraftingRecipe(), 
				tool.getRepairItem(), 
				this.itemData.getInteger("currentDurability"),
				this.itemData.getInteger("durability", tool.getDurability()), 
				this.itemData.getInteger("upgrades", tool.getUpgrades())
		);
	}
	
	public boolean isPickaxe() {
		return this.item instanceof Pickaxe;
	}
	
	public Pickaxe getPickaxe() {
		Pickaxe pickaxe = (Pickaxe) this.item;
		return new Pickaxe(
				pickaxe.getName(), 
				this.itemData.get("price", pickaxe.getPrice()), 
				pickaxe.getCraftingRecipe(), 
				pickaxe.getRepairItem(), 
				this.itemData.getInteger("minimumYeild", pickaxe.getMinimumYield()), 
				this.itemData.getInteger("maximumYeild", pickaxe.getMaximumYield()),
				this.itemData.getInteger("currentDurability"),
				this.itemData.getInteger("durability", pickaxe.getDurability()), 
				this.itemData.get("multiplier", pickaxe.getMultiplier()),
				this.itemData.getInteger("upgrades", pickaxe.getUpgrades())
		);
	}
	
	public boolean isRod() {
		return this.item instanceof Rod;
	}
	
	public Rod getRod() {
		Rod rod = (Rod) this.item;
		return new Rod(
				rod.getName(), 
				this.itemData.get("price", rod.getPrice()), 
				rod.getCraftingRecipe(), 
				rod.getRepairItem(), 
				this.itemData.getInteger("minimumYeild", rod.getMinimumYield()),
				this.itemData.getInteger("maximumYeild", rod.getMaximumYield()),
				this.itemData.getInteger("currentDurability"),
				this.itemData.getInteger("durability", rod.getDurability()), 
				this.itemData.getInteger("upgrades", rod.getUpgrades())
		);
	}
	
	public boolean isAxe() {
		return this.item instanceof Axe;
	}
	
	public Axe getAxe() {
		Axe axe = (Axe) this.item;
		return new Axe(
				axe.getName(), 
				this.itemData.get("price", axe.getPrice()), 
				axe.getCraftingRecipe(), 
				axe.getRepairItem(), 
				this.itemData.getInteger("maximumMaterials", axe.getMaximumMaterials()),
				this.itemData.getInteger("currentDurability"),
				this.itemData.getInteger("durability", axe.getDurability()), 
				this.itemData.get("multiplier", axe.getMultiplier()),
				this.itemData.getInteger("upgrades", axe.getUpgrades())
		);
	}
	
}
