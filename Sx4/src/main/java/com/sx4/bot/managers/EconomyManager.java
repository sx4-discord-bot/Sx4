package com.sx4.bot.managers;

import com.sx4.bot.entities.economy.item.*;
import com.sx4.bot.utility.SearchUtility;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;

public class EconomyManager {

	private final Map<Class<?>, List<Item>> items;
	private final Map<Integer, Item> itemCache;
	
	public EconomyManager() {
		this.items = new HashMap<>();
		this.itemCache = new HashMap<>();
		
		this.loadItems();
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

	public <Type extends Item> Type getItemById(int id, Class<Type> type) {
		return (Type) this.itemCache.get(id);
	}

	public List<? extends Item> getItems() {
		return this.getItems(Item.class);
	}
	
	public List<? extends Item> getItems(Class<? extends Item> type) {
		return this.items.getOrDefault(type, Collections.emptyList());
	}
	
	public Item getItemByName(String name) {
		return this.getItemByName(name, Item.class);
	}
	
	public <Type extends Item> Type getItemByName(String name, Class<Type> type) {
		return this.getItems().stream()
			.filter(item -> item.getName().equalsIgnoreCase(name))
			.map(type::cast)
			.findFirst()
			.orElse(null);
	}
	
	@SuppressWarnings("unchecked")
	public <Type extends Item> Type getItemByName(String name, Type defaultValue) {
		return this.getItems().stream()
			.filter(item -> item.getName().equalsIgnoreCase(name))
			.map(item -> (Type) item)
			.findFirst()
			.orElse(defaultValue);
	}

	@SuppressWarnings("unchecked")
	public <Type extends Item> Type getItemByQuery(String query, Class<Type> type) {
		return (Type) SearchUtility.find(this.getItems(type), query, Collections.singletonList(Item::getName));
	}

	
	public void reloadItems() {
		this.itemCache.clear();
		this.items.clear();

		this.loadItems();
	}
	
	public void loadItems() {
		try (FileInputStream stream = new FileInputStream("economy.json")) {
			this.addItems(new JSONObject(new String(stream.readAllBytes())));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public void addItems(JSONObject json) {
		for (ItemType type : ItemType.values()) {
			JSONArray items = json.optJSONArray(type.getDataName());
			if (items == null) {
				continue;
			}
			
			for (int i = 0; i < items.length(); i++) {
				JSONObject itemData = items.getJSONObject(i);
				
				Item item;
				switch (type) {
					case MATERIAL:
						item = new Material(itemData.getInt("id"), itemData.getString("name"), itemData.getLong("price"), itemData.getString("emote"), itemData.optBoolean("hidden", false));
						
						break;
					case WOOD:
						item = new Wood(itemData.getInt("id"), itemData.getString("name"), itemData.getLong("price"));
						
						break;
					case ENVELOPE:
						item = new Envelope(itemData.getInt("id"), itemData.getString("name"), itemData.getLong("price"));
						
						break;
					case MINER:
						item = new Miner(itemData.getInt("id"), itemData.getString("name"), itemData.getLong("price"), itemData.getLong("maxMaterials"), itemData.getDouble("multiplier"));
						
						break;
					case FACTORY:
						JSONObject cost = itemData.getJSONObject("cost");

						Material material = this.getItemById(cost.getInt("material"), Material.class);
						ItemStack<Material> itemStack = new ItemStack<>(material, cost.getLong("amount"));
						
						item = new Factory(itemData.getInt("id"), itemData.getString("name"), itemStack, itemData.getLong("minYield"), itemData.getLong("maxYield"));
						
						break;
					case CRATE:
						//JSONObject contentsData = itemData.getJSONObject("contents");
						//Map<ItemType, Long> contents = contentsData.keySet().stream().collect(Collectors.toMap(ItemType::fromDataName, contentsData::getLong));
						
						item = new Crate(itemData.getInt("id"), itemData.getString("name"), itemData.getLong("price"), itemData.optLong("credits", -1), new HashMap<>());
						
						break;
					case BOOSTER:
						item = new Booster(itemData.getInt("id"), itemData.getString("name"), itemData.getLong("price"));
						
						break;
					case ROD:
						JSONArray rodArray = itemData.getJSONArray("craft");
						
						List<ItemStack<Material>> rodCraft = new ArrayList<>();
						for (int c = 0; c < rodArray.length(); c++) {
							JSONObject craftData = rodArray.getJSONObject(c);
							Material craftMaterial = this.getItemById(craftData.getInt("material"), Material.class);
							
							rodCraft.add(new ItemStack<>(craftMaterial, craftData.getLong("amount")));
						}

						int rodRepairItemId = itemData.optInt("repairItem", -1);
						Material rodRepairItem = rodRepairItemId == -1 ? null : this.getItemById(rodRepairItemId, Material.class);
						
						item = new Rod(itemData.getInt("id"), itemData.getString("name"), itemData.getLong("price"), itemData.getInt("durability"), rodCraft, rodRepairItem, itemData.getLong("minYield"), itemData.getLong("maxYield"));
						
						break;
					case AXE:
						JSONArray axeArray = itemData.getJSONArray("craft");
						
						List<ItemStack<Material>> axeCraft = new ArrayList<>();
						for (int c = 0; c < axeArray.length(); c++) {
							JSONObject craftData = axeArray.getJSONObject(c);
							Material craftMaterial = this.getItemById(craftData.getInt("material"), Material.class);
							
							axeCraft.add(new ItemStack<>(craftMaterial, craftData.getLong("amount")));
						}

						int axeRepairItemId = itemData.optInt("repairItem", -1);
						Material axeRepairItem = axeRepairItemId == -1 ? null : this.getItemById(axeRepairItemId, Material.class);
						
						item = new Axe(itemData.getInt("id"), itemData.getString("name"), itemData.getLong("price"), itemData.getInt("durability"), axeCraft, axeRepairItem, itemData.getLong("maxMaterials"), itemData.getDouble("multiplier"));
						
						break;
					case PICKAXE:
						JSONArray pickaxeArray = itemData.getJSONArray("craft");
						
						List<ItemStack<Material>> pickaxeCraft = new ArrayList<>();
						for (int c = 0; c < pickaxeArray.length(); c++) {
							JSONObject craftData = pickaxeArray.getJSONObject(c);
							Material craftMaterial = this.getItemById(craftData.getInt("material"), Material.class);
							
							pickaxeCraft.add(new ItemStack<>(craftMaterial, craftData.getLong("amount")));
						}

						int pickaxeRepairItemId = itemData.optInt("repairItem", -1);
						Material pickaxeRepairItem = pickaxeRepairItemId == -1 ? null : this.getItemById(pickaxeRepairItemId, Material.class);
						
						item = new Pickaxe(itemData.getInt("id"), itemData.getString("name"), itemData.getLong("price"), itemData.getInt("durability"), pickaxeCraft, pickaxeRepairItem, itemData.getLong("minYield"), itemData.getLong("maxYield"), itemData.getDouble("multiplier"));
						
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
	
}
