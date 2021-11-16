package com.sx4.bot.managers;

import com.sx4.bot.entities.economy.item.*;
import com.sx4.bot.utility.SearchUtility;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.FileInputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.*;

public class EconomyManager {

	private final Random random;

	private final Map<Class<?>, List<Item>> items;
	private final Map<Integer, Item> itemCache;
	
	public EconomyManager() {
		this.items = new HashMap<>();
		this.itemCache = new HashMap<>();
		this.random = new SecureRandom();
		
		this.loadItems();
	}

	public Random getRandom() {
		return this.random;
	}

	public void addItem(Class<?> type, Item item) {
		this.items.compute(type, (key, value) -> {
			if (value == null) {
				List<Item> itemsOfType = new ArrayList<>();
				itemsOfType.add(item);

				return itemsOfType;
			} else {
				value.add(item);

				return value;
			}
		});
	}
	
	public Map<Integer, Item> getItemCache() {
		return this.itemCache;
	}

	public Item getItemById(int id) {
		return this.itemCache.get(id);
	}

	@SuppressWarnings("unchecked")
	public <Type extends Item> Type getItemById(int id, Class<Type> type) {
		return (Type) this.itemCache.get(id);
	}

	public List<Item> getItems() {
		return this.getItems(Item.class);
	}

	@SuppressWarnings("unchecked")
	public <Type extends Item> List<Type> getItems(Class<Type> type) {
		return (List<Type>) new ArrayList<>(this.items.getOrDefault(type, Collections.emptyList()));
	}
	
	public Item getItemByName(String name) {
		return this.getItemByName(name, Item.class);
	}
	
	public <Type extends Item> Type getItemByName(String name, Class<Type> type) {
		return this.getItems(type).stream()
			.filter(item -> item.getName().equalsIgnoreCase(name))
			.findFirst()
			.orElse(null);
	}
	
	@SuppressWarnings("unchecked")
	public <Type extends Item> Type getItemByName(String name, Type defaultValue) {
		return this.getItems(defaultValue.getClass()).stream()
			.filter(item -> item.getName().equalsIgnoreCase(name))
			.map(item -> (Type) item)
			.findFirst()
			.orElse(defaultValue);
	}

	public <Type extends Item> Type getItemByQuery(String query, Class<Type> type) {
		return SearchUtility.find(this.getItems(type), query, Collections.singletonList(Item::getName));
	}

	public void reloadItems() {
		this.itemCache.clear();
		this.items.clear();

		this.loadItems();
	}
	
	public void loadItems() {
		try (FileInputStream stream = new FileInputStream("economy.json")) {
			JSONObject json = new JSONObject(new String(stream.readAllBytes()));
			this.addItems(json);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public void addItems(JSONObject json) {
		JSONArray items = json.optJSONArray("items");
		if (items == null) {
			return;
		}

		for (int i = 0; i < items.length(); i++) {
			JSONObject itemData = items.getJSONObject(i);

			ItemType type = ItemType.fromId(itemData.getInt("type"));
			if (type == null) {
				continue;
			}

			Item item;
			switch (type) {
				case MATERIAL:
					item = new Material(this, itemData.getInt("id"), itemData.getString("name"), itemData.getLong("price"), itemData.getString("emote"), itemData.optBoolean("hidden", false));

					break;
				case WOOD:
					item = new Wood(this, itemData.getInt("id"), itemData.getString("name"), itemData.getLong("price"));

					break;
				case ENVELOPE:
					item = new Envelope(this, itemData.getInt("id"), itemData.getString("name"), itemData.getLong("price"));

					break;
				case MINER:
					item = new Miner(this, itemData.getInt("id"), itemData.getString("name"), itemData.getLong("price"), itemData.getLong("maxMaterials"), itemData.getDouble("multiplier"));

					break;
				case FACTORY:
					JSONObject cost = itemData.getJSONObject("cost");

					Material material = this.getItemById(cost.getInt("material"), Material.class);
					ItemStack<Material> itemStack = new ItemStack<>(material, cost.getLong("amount"));

					item = new Factory(this, itemData.getInt("id"), itemData.getString("name"), itemStack, itemData.getLong("minYield"), itemData.getLong("maxYield"));

					break;
				case CRATE:
					JSONArray contentsData = itemData.optJSONArray("contents");

					Map<ItemType, Long> contents = new HashMap<>();
					if (contentsData != null) {
						for (int c = 0; c < contentsData.length(); c++) {
							JSONObject data = contentsData.getJSONObject(c);
							contents.put(ItemType.fromId(data.getInt("type")), data.getLong("amount"));
						}
					}

					item = new Crate(this, itemData.getInt("id"), itemData.getString("name"), itemData.getLong("price"), itemData.optLong("credits", -1), itemData.optBoolean("hidden", false), itemData.optBoolean("openable", true), contents);

					break;
				case ROD:
					JSONArray rodArray = itemData.getJSONArray("craft");

					List<ItemStack<CraftItem>> rodCraft = new ArrayList<>();
					for (int c = 0; c < rodArray.length(); c++) {
						JSONObject craftData = rodArray.getJSONObject(c);
						CraftItem craftItem = this.getItemById(craftData.getInt("item"), CraftItem.class);

						rodCraft.add(new ItemStack<>(craftItem, craftData.getLong("amount")));
					}

					int rodRepairItemId = itemData.optInt("repairItem", -1);
					CraftItem rodRepairItem = rodRepairItemId == -1 ? null : this.getItemById(rodRepairItemId, CraftItem.class);

					item = new Rod(this, itemData.getInt("id"), itemData.getString("name"), itemData.getLong("price"), itemData.getInt("durability"), rodCraft, rodRepairItem, itemData.getLong("minYield"), itemData.getLong("maxYield"));

					break;
				case AXE:
					JSONArray axeArray = itemData.getJSONArray("craft");

					List<ItemStack<CraftItem>> axeCraft = new ArrayList<>();
					for (int c = 0; c < axeArray.length(); c++) {
						JSONObject craftData = axeArray.getJSONObject(c);
						CraftItem craftItem = this.getItemById(craftData.getInt("item"), CraftItem.class);

						axeCraft.add(new ItemStack<>(craftItem, craftData.getLong("amount")));
					}

					int axeRepairItemId = itemData.optInt("repairItem", -1);
					CraftItem axeRepairItem = axeRepairItemId == -1 ? null : this.getItemById(axeRepairItemId, CraftItem.class);

					item = new Axe(this, itemData.getInt("id"), itemData.getString("name"), itemData.getLong("price"), itemData.getInt("durability"), axeCraft, axeRepairItem, itemData.getLong("maxMaterials"), itemData.getDouble("multiplier"));

					break;
				case PICKAXE:
					JSONArray pickaxeArray = itemData.getJSONArray("craft");

					List<ItemStack<CraftItem>> pickaxeCraft = new ArrayList<>();
					for (int c = 0; c < pickaxeArray.length(); c++) {
						JSONObject craftData = pickaxeArray.getJSONObject(c);
						CraftItem craftItem = this.getItemById(craftData.getInt("item"), CraftItem.class);

						pickaxeCraft.add(new ItemStack<>(craftItem, craftData.getLong("amount")));
					}

					int pickaxeRepairItemId = itemData.optInt("repairItem", -1);
					CraftItem pickaxeRepairItem = pickaxeRepairItemId == -1 ? null : this.getItemById(pickaxeRepairItemId, CraftItem.class);

					item = new Pickaxe(this, itemData.getInt("id"), itemData.getString("name"), itemData.getLong("price"), itemData.getInt("durability"), pickaxeCraft, pickaxeRepairItem, itemData.getLong("minYield"), itemData.getLong("maxYield"), itemData.getDouble("multiplier"));

					break;
				default:
					item = null;
			}

			if (item != null) {
				Class<?> itemType = item.getClass();
				do {
					this.addItem(itemType, item);
				} while ((itemType = itemType.getSuperclass()) != null && itemType != Object.class);

				this.itemCache.put(item.getId(), item);
			}
		}
	}
	
}
