package com.sx4.utils;

import static com.rethinkdb.RethinkDB.r;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.rethinkdb.gen.ast.Insert;
import com.sx4.economy.Item;
import com.sx4.economy.ItemStack;
import com.sx4.economy.items.Booster;
import com.sx4.economy.items.Crate;
import com.sx4.economy.items.Factory;
import com.sx4.economy.items.Miner;
import com.sx4.economy.materials.Material;
import com.sx4.economy.materials.Wood;
import com.sx4.economy.tools.Axe;
import com.sx4.economy.tools.Pickaxe;
import com.sx4.economy.tools.Rod;
import com.sx4.settings.Settings;

import net.dv8tion.jda.core.utils.tuple.Pair;
import net.dv8tion.jda.core.entities.User;

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
			return (1 / Math.pow((double) this.chance / Slot.getTotal(), 3)) * 0.55;
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
	
	public static boolean hasAxe(List<Map<String, Object>> items) {
		for (Map<String, Object> item : items) {
			for (Axe axe : Axe.ALL) {
				if (item.get("name").equals(axe.getName())) {
					return true;
				}
			}
		}
		
		return false;
	}
	
	@SuppressWarnings("unchecked")
	public static Axe getUserAxe(Map<String, Object> data) {
		List<Map<String, Object>> items = (List<Map<String, Object>>) data.get("items");
		for (Map<String, Object> item : items) {
			for (Axe axe : Axe.ALL) {
				if (item.get("name").equals(axe.getName())) {
					return new Axe(axe.getName(), 
							item.containsKey("price") ? (long) item.get("price") : axe.getPrice(), 
							axe.getCraftingRecipe(), 
							axe.getRepairItem(), 
							item.containsKey("max_mats") ? Math.toIntExact((long) item.get("max_mats")) : axe.getMaximumMaterials(), 
							Math.toIntExact((long) data.get("axedur")),
							item.containsKey("durability") ? Math.toIntExact((long) item.get("durability")) : axe.getDurability(), 
							item.containsKey("multiplier") ? (double) item.get("multiplier") : axe.getMultiplier(),
							item.containsKey("upgrades") ? Math.toIntExact((long) item.get("upgrades")) : axe.getUpgrades());
				}
			}
		}
		
		return null;
	}
	
	public static boolean hasRod(List<Map<String, Object>> items) {
		for (Map<String, Object> item : items) {
			for (Rod rod : Rod.ALL) {
				if (item.get("name").equals(rod.getName())) {
					return true;
				}
			}
		}
		
		return false;
	}
	
	@SuppressWarnings("unchecked")
	public static Rod getUserRod(Map<String, Object> data) {
		List<Map<String, Object>> items = (List<Map<String, Object>>) data.get("items");
		for (Map<String, Object> item : items) {
			for (Rod rod : Rod.ALL) {
				if (item.get("name").equals(rod.getName())) {
					return new Rod(rod.getName(), 
							item.containsKey("price") ? (long) item.get("price") : rod.getPrice(), 
							rod.getCraftingRecipe(), 
							rod.getRepairItem(), 
							item.containsKey("rand_min") ? Math.toIntExact((long) item.get("rand_min")) : rod.getMinimumYield(), 
							item.containsKey("rand_max") ? Math.toIntExact((long) item.get("rand_max")) : rod.getMaximumYield(),
							Math.toIntExact((long) data.get("roddur")),
							item.containsKey("durability") ? Math.toIntExact((long) item.get("durability")) : rod.getDurability(), 
							item.containsKey("upgrades") ? Math.toIntExact((long) item.get("upgrades")) : rod.getUpgrades());
				}
			}
		}
		
		return null;
	}
	
	public static boolean hasPickaxe(List<Map<String, Object>> items) {
		for (Map<String, Object> item : items) {
			for (Pickaxe pickaxe : Pickaxe.ALL) {
				if (item.get("name").equals(pickaxe.getName())) {
					return true;
				}
			}
		}
		
		return false;
	}
	
	@SuppressWarnings("unchecked")
	public static Pickaxe getUserPickaxe(Map<String, Object> data) {
		List<Map<String, Object>> items = (List<Map<String, Object>>) data.get("items");
		for (Map<String, Object> item : items) {
			for (Pickaxe pickaxe : Pickaxe.ALL) {
				if (item.get("name").equals(pickaxe.getName())) {
					return new Pickaxe(pickaxe.getName(), 
							item.containsKey("price") ? (long) item.get("price") : pickaxe.getPrice(), 
							pickaxe.getCraftingRecipe(), 
							pickaxe.getRepairItem(), 
							item.containsKey("rand_min") ? Math.toIntExact((long) item.get("rand_min")) : pickaxe.getMinimumYield(), 
							item.containsKey("rand_max") ? Math.toIntExact((long) item.get("rand_max")) : pickaxe.getMaximumYield(),
							Math.toIntExact((long) data.get("pickdur")),
							item.containsKey("durability") ? Math.toIntExact((long) item.get("durability")) : pickaxe.getDurability(), 
							item.containsKey("multiplier") ? (item.get("multiplier") instanceof Long ? (double) (long) item.get("multiplier") : (double) item.get("multiplier")) : pickaxe.getMultiplier(),
							item.containsKey("upgrades") ? Math.toIntExact((long) item.get("upgrades")) : pickaxe.getUpgrades());
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
	
	public static Item getTradeableItem(String itemName) {
		itemName = itemName.toLowerCase();
		for (Item item : TRADEABLE_ITEMS) {
			if (item.getName().toLowerCase().equals(itemName)) {
				return item;
			}
		}
		
		return null;
	}
	
	@SuppressWarnings("unchecked")
	public static long getUserNetworth(Map<String, Object> data) {
		long networth = 0;
		List<Map<String, Object>> items = (List<Map<String, Object>>) data.get("items");
		for (Map<String, Object> itemData : items) {
			Item item = EconomyUtils.getItem((String) itemData.get("name"));
			if (item == null) {
				System.out.println((String) itemData.get("name") + "-" + (String) data.get("id"));
			}
			ItemStack userItem = EconomyUtils.getUserItem(items, item);
			if (item.isBuyable()) {
				networth += userItem.getItem().getPrice() * userItem.getAmount();
			}
		}
		
		networth += (long) data.get("balance");
		
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
	
	public static Map<String, Object> getUserItemRaw(List<Map<String, Object>> items, Item item) {
		for (Map<String, Object> itemData : items) {
			if (itemData.get("name").equals(item.getName())) {
				return itemData;
			}
		}
		
		return null;
	}
	
	public static ItemStack getUserItem(List<Map<String, Object>> items, Item item) {
		for (Map<String, Object> itemData : items) {
			String itemName = (String) itemData.get("name");
			if (itemName.equals(item.getName())) {
				if (itemData.containsKey("price")) {
					return new ItemStack(new Item(itemName, (long) itemData.get("price")), (long) itemData.get("amount"));
				} else {
					return new ItemStack(item, (long) itemData.get("amount"));
				}
			}
		}
		
		return new ItemStack(item, 0L);
	}
	
	public static List<Map<String, Object>> editItem(List<Map<String, Object>> currentItems, Item item, Map<String, Object> extra) {
		List<Map<String, Object>> items = new ArrayList<>(currentItems);
		for (Map<String, Object> itemData : items) {
			if (itemData.get("name").equals(item.getName())) {
				items.remove(itemData);
				itemData.putAll(extra);
				items.add(itemData);
				
				return items;
			}
		}
		
		return items;
	}
	
	public static List<Map<String, Object>> addItemsFromRaw(List<Map<String, Object>> currentItems, Map<String, Object> rawData) {
		List<Map<String, Object>> items = new ArrayList<>(currentItems);
		for (Map<String, Object> itemData : items) {
			if (itemData.get("name").equals(rawData.get("name"))) {
				items.remove(itemData);
				rawData.put("amount", ((long) itemData.get("amount")) + (long) rawData.get("amount"));
				items.add(rawData);
				
				return items;
			}
		}
		
		items.add(rawData);
		
		return items;
	}
	
	public static List<Map<String, Object>> addItem(List<Map<String, Object>> currentItems, Item item, Map<String, Object> extra) {
		List<Map<String, Object>> items = new ArrayList<>(currentItems);
		for (Map<String, Object> itemData : items) {
			if (itemData.get("name").equals(item.getName())) {
				items.remove(itemData);
				itemData.put("amount", ((long) itemData.get("amount")) + 1);
				itemData.putAll(extra);
				items.add(itemData);
				
				return items;
			}
		}
		
		Map<String, Object> newItem = new HashMap<>();
		newItem.put("name", item.getName());
		newItem.put("amount", 1);
		newItem.putAll(extra);
		items.add(newItem);
		
		return items;
	}
	
	public static List<Map<String, Object>> addItem(List<Map<String, Object>> currentItems, Item item) {
		return addItems(currentItems, item, 1);
	}
	
	public static List<Map<String, Object>> addItems(List<Map<String, Object>> currentItems, Item item, long amount) {
		List<Map<String, Object>> items = new ArrayList<>(currentItems);
		for (Map<String, Object> itemData : items) {
			if (itemData.get("name").equals(item.getName())) {
				items.remove(itemData);
				itemData.put("amount", ((long) itemData.get("amount")) + amount);
				items.add(itemData);
				
				return items;
			}
		}
		
		Map<String, Object> newItem = new HashMap<>();
		newItem.put("name", item.getName());
		newItem.put("amount", amount);
		items.add(newItem);
		
		return items;
	}
	
	public static List<Map<String, Object>> removeItem(List<Map<String, Object>> currentItems, Item item) {
		return removeItems(currentItems, item, 1);
	}
	
	public static List<Map<String, Object>> removeItems(List<Map<String, Object>> currentItems, Item item, long amount) {
		List<Map<String, Object>> items = new ArrayList<>(currentItems);
		for (Map<String, Object> itemData : items) {
			if (itemData.get("name").equals(item.getName())) {
				int newAmount = (int) (((long) itemData.get("amount")) - amount);
				items.remove(itemData);
				if (newAmount != 0) {
					itemData.put("amount", newAmount);
					items.add(itemData);
				}
				
				return items;
			}
		}
		
		return items;
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
	
	public static Insert insertData(User user) {
		 return r.table("bank").insert(r.hashMap("id", user.getId())
				.with("rep", 0)
				.with("balance", Settings.STARTING_BALANCE)
				.with("streak", 0)
				.with("streaktime", null)
				.with("reptime", null)
				.with("items", new Object[0])
				.with("pickdur", null)
				.with("roddur", null)
				.with("axedur", null)
				.with("axetime", null)
				.with("minertime", null)
				.with("winnings", 0)
				.with("fishtime", null)
				.with("factorytime", null)
				.with("picktime", null));
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
