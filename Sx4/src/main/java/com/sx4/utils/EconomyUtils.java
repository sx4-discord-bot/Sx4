package com.sx4.utils;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bson.Document;
import org.bson.conversions.Bson;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.sx4.database.Database;
import com.sx4.economy.Item;
import com.sx4.economy.ItemStack;
import com.sx4.economy.items.Booster;
import com.sx4.economy.items.Crate;
import com.sx4.economy.items.Factory;
import com.sx4.economy.items.Miner;
import com.sx4.economy.items.Sawmill;
import com.sx4.economy.materials.Material;
import com.sx4.economy.materials.Wood;
import com.sx4.economy.tools.Axe;
import com.sx4.economy.tools.Pickaxe;
import com.sx4.economy.tools.Rod;

import net.dv8tion.jda.internal.utils.tuple.Pair;

public class EconomyUtils {
	
	public static final long FISH_COOLDOWN = 300;
	public static final long CHOP_COOLDOWN = 600;
	public static final long MINE_COOLDOWN = 900;
	public static final long MINER_COOLDOWN = 7200;
	public static final long FACTORY_COOLDOWN = 43200;
	public static final long VOTE_COOLDOWN = 43200;
	public static final long DAILY_COOLDOWN = 86400;
	public static final long REPUTATION_COOLDOWN = 86400;
	
	public static final List<Item> WINNABLE_ITEMS = new ArrayList<>(); 
	public static final List<Item> ALL_ITEMS = new ArrayList<>();
	public static final List<Item> TRADEABLE_ITEMS = new ArrayList<>();
	static {
		for (Miner miner : Miner.ALL) {
			WINNABLE_ITEMS.add(miner);
			ALL_ITEMS.add(miner);
			TRADEABLE_ITEMS.add(miner);
		}
		
		for (Sawmill sawmill : Sawmill.ALL) {
			WINNABLE_ITEMS.add(sawmill);
			ALL_ITEMS.add(sawmill);
			TRADEABLE_ITEMS.add(sawmill);
		}
		
		for (Material material : Material.ALL) {
			if (!material.isHidden()) {
				WINNABLE_ITEMS.add(material);
			}
			
			ALL_ITEMS.add(material);
			TRADEABLE_ITEMS.add(material);
		}
		
		for (Factory factory : Factory.ALL) {
			if (!factory.isHidden()) {
				WINNABLE_ITEMS.add(factory);
			}
			
			ALL_ITEMS.add(factory);
			TRADEABLE_ITEMS.add(factory);
		}
		
		for (Crate crate : Crate.ALL) {
			if (crate.isBuyable()) {
				WINNABLE_ITEMS.add(crate);
			}
			
			ALL_ITEMS.add(crate);
			TRADEABLE_ITEMS.add(crate);
		}
		
		for (Wood wood : Wood.ALL) {
			WINNABLE_ITEMS.add(wood);
			ALL_ITEMS.add(wood);
			TRADEABLE_ITEMS.add(wood);
		}
		
		for (Booster booster : Booster.ALL) {
			ALL_ITEMS.add(booster);
			TRADEABLE_ITEMS.add(booster);
		}
		
		for (Rod rod : Rod.ALL) {
			ALL_ITEMS.add(rod);
		}
		
		for (Pickaxe pickaxe : Pickaxe.ALL) {
			ALL_ITEMS.add(pickaxe);
		}
		
		for (Axe axe : Axe.ALL) {
			ALL_ITEMS.add(axe);
		}
	}
	
	public enum Slot {
		DIAMOND(1, Material.DIAMOND),
		PLATINUM(2, Material.PLATINUM),
		BITCOIN(5, Material.BITCOIN),
		TITANIUM(13, Material.TITANIUM),
		OIL(37, Material.OIL),
		GOLD(110, Material.GOLD),
		ALUMINIUM(205, Material.ALUMINIUM),
		IRON(450, Material.IRON),
		COPPER(1000, Material.COPPER),
		COAL(1600, Material.COAL),
		SHOE(2500, Material.SHOE);
		
		private long chance;
		private Material material;
		
		private Slot(long chance, Material material) {
			this.chance = chance;
			this.material = material;
		}
		
		public long getChance() {
			return this.chance;
		}
		
		public Material getMaterial() {
			return this.material;
		}
		
		public double getMultiplier() {
			return (1 / Math.pow((double) this.chance / Slot.getTotal(), 3)) * 0.16;
		}
		
		public Slot getAbove() {
			Slot[] slotValues = Slot.values();
			int slotLength = slotValues.length;
			
			int index = -1;
			for (int i = 0; i < slotLength; i++) {
				if (slotValues[i].equals(this)) {
					index = i;
				}
			}
			
			if (index == slotLength - 1) {
				index = 0;
			} else {
				index += 1;
			}
			
			return slotValues[index];
		}
		
		public Slot getBelow() {
			Slot[] slotValues = Slot.values();
			int slotLength = slotValues.length;
			
			int index = -1;
			for (int i = 0; i < slotLength; i++) {
				if (slotValues[i].equals(this)) {
					index = i;
				}
			}
			
			if (index == 0) {
				index = slotLength - 1;
			} else {
				index -= 1;
			}
			
			return slotValues[index];
		}
		
		public static long getTotal() {
			long total = 0;
			for (Slot slot : Slot.values()) {
				total += slot.getChance();
			}
			
			return total;
		}
	}
	
	public static boolean hasAxe(List<Document> items) {
		for (Document item : items) {
			for (Axe axe : Axe.ALL) {
				if (item.getString("name").equals(axe.getName())) {
					return true;
				}
			}
		}
		
		return false;
	}
	
	public static Axe getUserAxe(List<Document> items) {
		for (Document item : items) {
			for (Axe axe : Axe.ALL) {
				if (item.getString("name").equals(axe.getName())) {
					return new Axe(
							axe.getName(), 
							item.get("price", axe.getPrice()), 
							axe.getCraftingRecipe(), 
							axe.getRepairItem(), 
							item.getInteger("maximumMaterials", axe.getMaximumMaterials()),
							item.getInteger("currentDurability"),
							item.getInteger("maximumDurability", axe.getDurability()), 
							item.get("multiplier", axe.getMultiplier()),
							item.getInteger("upgrades", axe.getUpgrades())
					);
				}
			}
		}
		
		return null;
	}
	
	public static boolean hasRod(List<Document> items) {
		for (Document item : items) {
			for (Rod rod : Rod.ALL) {
				if (item.getString("name").equals(rod.getName())) {
					return true;
				}
			}
		}
		
		return false;
	}
	
	public static Rod getUserRod(List<Document> items) {
		for (Document item : items) {
			for (Rod rod : Rod.ALL) {
				if (item.getString("name").equals(rod.getName())) {
					return new Rod(
							rod.getName(), 
							item.get("price", rod.getPrice()), 
							rod.getCraftingRecipe(), 
							rod.getRepairItem(), 
							item.getInteger("minimumYield", rod.getMinimumYield()), 
							item.getInteger("maximumYield", rod.getMaximumYield()), 
							item.getInteger("currentDurability"),
							item.getInteger("maximumDurability", rod.getDurability()), 
							item.getInteger("upgrades", rod.getUpgrades())
					);
				}
			}
		}
		
		return null;
	}
	
	public static boolean hasPickaxe(List<Document> items) {
		for (Document item : items) {
			for (Pickaxe pickaxe : Pickaxe.ALL) {
				if (item.getString("name").equals(pickaxe.getName())) {
					return true;
				}
			}
		}
		
		return false;
	}
	
	public static Pickaxe getUserPickaxe(List<Document> items) {
		for (Document item : items) {
			for (Pickaxe pickaxe : Pickaxe.ALL) {
				if (item.getString("name").equals(pickaxe.getName())) {
					return new Pickaxe(
							pickaxe.getName(), 
							item.get("price", pickaxe.getPrice()), 
							pickaxe.getCraftingRecipe(), 
							pickaxe.getRepairItem(), 
							item.getInteger("minimumYield", pickaxe.getMinimumYield()), 
							item.getInteger("maximumYield", pickaxe.getMaximumYield()),
							item.getInteger("currentDurability"),
							item.getInteger("maximumDurability", pickaxe.getDurability()), 
							item.get("multiplier", pickaxe.getMultiplier()),
							item.getInteger("upgrades", pickaxe.getUpgrades())
					);
				}
			}
		}
		
		return null;
	}
	
	public static Pair<Long, List<ItemStack>> getTrade(String tradeArgument) {
		String[] splitTrade = tradeArgument.split(",");
		long money = 0;
		List<ItemStack> itemStacks = new ArrayList<>();
		for (String split : splitTrade) {
			split = split.trim();
			try {
				money += Long.valueOf(split);
			} catch(NumberFormatException e) {
				Pair<String, BigInteger> itemPair = getItemAndAmount(split);
				Item item = getTradeableItem(itemPair.getLeft());
				if (item == null) {
					throw new IllegalArgumentException("The item `" + split + "` is not tradeable or doesn't exist :no_entry:");
				} else {
					boolean updated = false;
					for (ItemStack itemStack : new ArrayList<>(itemStacks)) {
						if (itemStack.getItem().equals(item)) {
							itemStack.addAmount(itemPair.getRight().longValue());
							
							updated = true;
							break;
						}
					}
					
					if (updated == false) {
						itemStacks.add(new ItemStack(item, itemPair.getRight().longValue()));
					}
				}
			}
		}
		
		if (money <= 0 && itemStacks.isEmpty()) {
			throw new IllegalArgumentException("You have to include at least something in the trade :no_entry:");
		}
		
		return Pair.of(money, itemStacks);
	}
	
	public static UpdateOneModel<Document> getAddItemModel(Long userId, List<Document> items, String name, long amount, Document extraFields) {
		Bson filter = userId != null ? Filters.eq("_id", userId) : null;
		for (Document itemData : items) {
			if (itemData.getString("name").equals(name)) {
				Bson update = Updates.inc("economy.items.$[" + name + "].amount", amount);
				for (String key : extraFields.keySet()) {
					update = Updates.combine(update, Updates.set("economy.items.$[" + name + "]." + key, extraFields.get(key)));
				}
				
				UpdateOptions updateOptions = new UpdateOptions().arrayFilters(List.of(Filters.eq(name + ".name", name))).upsert(true);
				return new UpdateOneModel<>(filter, update, updateOptions);
			}
		}
		
		Document newItem = new Document("name", name).append("amount", amount);
		newItem.putAll(extraFields);
		return new UpdateOneModel<>(filter, Updates.push("economy.items", newItem), new UpdateOptions().upsert(true));
	}
	
	public static UpdateOneModel<Document> getAddItemModel(List<Document> items, String name, long amount, Document extraFields) {
		return EconomyUtils.getAddItemModel(null, items, name, amount, extraFields);
	}
	
	public static UpdateOneModel<Document> getAddItemModel(Long userId, List<Document> items, Document item) {
		Document extraFields = item;
		extraFields.remove("name");
		extraFields.remove("amount");
		return EconomyUtils.getAddItemModel(userId, items, item.getString("name"), item.getLong("amount"), extraFields);
	}
	
	public static UpdateOneModel<Document> getAddItemModel(List<Document> items, Document item) {
		return EconomyUtils.getAddItemModel(null, items, item);
	}
	
	public static UpdateOneModel<Document> getAddItemModel(Long userId, List<Document> items, Item item, long amount, Document extraFields) {
		return EconomyUtils.getAddItemModel(userId, items, item.getName(), amount, extraFields);
	}
	
	public static UpdateOneModel<Document> getAddItemModel(Long userId, List<Document> items, Item item, long amount) {
		return EconomyUtils.getAddItemModel(userId, items, item, amount, Database.EMPTY_DOCUMENT);
	}
	
	public static UpdateOneModel<Document> getAddItemModel(List<Document> items, Item item, long amount, Document extraFields) {
		return EconomyUtils.getAddItemModel(null, items, item, amount, extraFields);
	}
	
	public static UpdateOneModel<Document> getAddItemModel(List<Document> items, Item item, long amount) {
		return EconomyUtils.getAddItemModel(null, items, item, amount, Database.EMPTY_DOCUMENT);
	}
	
	public static UpdateOneModel<Document> getAddItemModel(List<Document> items, ItemStack itemStack) {
		return EconomyUtils.getAddItemModel(items, itemStack.getItem(), itemStack.getAmount());
	}
	
	public static UpdateOneModel<Document> getRemoveItemModel(Long userId, List<Document> items, Item item, long amount) {
		Bson filter = userId != null ? Filters.eq("_id", userId) : null;
		for (Document itemData : items) {
			if (itemData.getString("name").equals(item.getName())) {
				UpdateOptions updateOptions = new UpdateOptions().upsert(true);
				long amountLeft = itemData.getLong("amount") - amount;
				if (amountLeft > 0) {
					updateOptions = updateOptions.arrayFilters(List.of(Filters.eq(item.getName() + ".name", item.getName())));
					return new UpdateOneModel<>(filter, Updates.inc("economy.items.$[" + item.getName() + "].amount", -amount), updateOptions);
				} else if (amountLeft == 0) {
					return new UpdateOneModel<>(filter, Updates.pull("economy.items", Filters.eq("name", item.getName())), updateOptions);
				} else {
					throw new IllegalArgumentException("Removal amount is more than the amount of items the user has");
				}
			}
		}
		
		throw new IllegalArgumentException("User doesn't have that item");
	}
	
	public static UpdateOneModel<Document> getRemoveItemModel(List<Document> items, Item item, long amount) {
		return EconomyUtils.getRemoveItemModel(null, items, item, amount);
	}
	
	public static UpdateOneModel<Document> getRemoveItemModel(List<Document> items, ItemStack itemStack) {
		return EconomyUtils.getRemoveItemModel(items, itemStack.getItem(), itemStack.getAmount());
	}
	
	public static Item getTradeableItem(String itemName) {
		itemName = itemName.toLowerCase();
		for (Item item : TRADEABLE_ITEMS) {
			if (item.getName().toLowerCase().equals(itemName)) {
				return item;
			}
		}
		
		return null;
	}
	
	public static long getUserNetworth(Document data) {
		long networth = 0;
		List<Document> items = data.getList("items", Document.class, Collections.emptyList());
		for (Document itemData : items) {
			Item item = EconomyUtils.getItem(itemData.getString("name"));
			ItemStack userItem = EconomyUtils.getUserItem(items, item);
			if (item.isBuyable()) {
				networth += userItem.getItem().getPrice() * userItem.getAmount();
			}
		}
		
		networth += data.get("balance", 0);
		
		return networth;
	}
	
	public static Item getItem(String itemName) {
		itemName = itemName.toLowerCase();
		for (Item item : ALL_ITEMS) {
			if (item.getName().toLowerCase().equals(itemName)) {
				return item;
			}
		}
		
		return null;
	}
	
	public static Document getUserItemRaw(List<Document> items, Item item) {
		for (Document itemData : items) {
			if (itemData.getString("name").equals(item.getName())) {
				return itemData;
			}
		}
		
		return null;
	}
	
	public static ItemStack getUserItem(List<Document> items, Item item) {
		for (Document itemData : items) {
			String itemName = itemData.getString("name");
			if (itemName.equals(item.getName())) {
				if (itemData.containsKey("price")) {
					return new ItemStack(new Item(itemName, itemData.getLong("price")), itemData.getLong("amount"));
				} else {
					return new ItemStack(item, itemData.getLong("amount"));
				}
			}
		}
		
		return new ItemStack(item, 0L);
	}
	
	private static Map<String, Double> stringValues = new HashMap<>();
	static {
		stringValues.put("all", 1D);
		stringValues.put("full", 1D);
		stringValues.put("half", 0.5D);
		stringValues.put("quarter", 0.25D);
		stringValues.put("eighth", 0.125D);
	}
	
	public static long convertMoneyArgument(long userAmount, String argument) {
		argument = argument.toLowerCase();
		
		long amount;
		try {
			amount = Long.valueOf(argument);
		} catch(NumberFormatException e) {
			if (stringValues.containsKey(argument)) {
				amount = (long) Math.round(userAmount * stringValues.get(argument));
			} else if (argument.endsWith("%")) {
				try {
					int percent = Integer.parseInt(argument.substring(0, argument.length() - 1));
					if (percent < 1 || percent > 100) {
						throw new IllegalArgumentException("The percentage cannot be less than 1 or more than 100 :no_entry:");
					}
					
					amount = (long) Math.round(userAmount * ((double) percent / 100));
				} catch(NumberFormatException ex) {
					throw new IllegalArgumentException("`" + argument + "` is not a valid percentage :no_entry:");
				}
			} else {
				throw new IllegalArgumentException("I could not determine an amount from that money argument :no_entry:");
			}
		}
		
		if (amount < 1) {
			throw new IllegalArgumentException("You can not provide less than $1 :no_entry:");
		}
		
		return amount;
	}
	
	public static Pair<String, BigInteger> getItemAndAmount(String argument) {
		BigInteger amount;
		String name;
		if (argument.contains(" ")) {
			String[] argumentSplit = argument.split(" ");
			try {
				String amountStringStart = argumentSplit[0];
				amount = new BigInteger(amountStringStart);
				name = argument.substring(amountStringStart.length() + 1);
			} catch(NumberFormatException e) {
				try {
					String amountStringEnd = argumentSplit[argumentSplit.length - 1];
					amount = new BigInteger(amountStringEnd);
					name = argument.substring(0, (argument.length() - amountStringEnd.length()) - 1);
				} catch(NumberFormatException ex) {
					amount = BigInteger.ONE;
					name = argument;
				}
			}
		} else {
			amount = BigInteger.ONE;
			name = argument;
		}
		
		return Pair.of(name, amount);
	}
}
