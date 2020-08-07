package com.sx4.bot.managers;

import com.sx4.bot.entities.economy.item.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class EconomyManager {

	private static final EconomyManager INSTANCE = new EconomyManager();
	
	public static EconomyManager get() {
		return EconomyManager.INSTANCE;
	}
	
	private final List<Item> items;
	
	private final Map<ItemType, List<Item>> itemTypes;
	
	private EconomyManager() {
		this.items = new ArrayList<>();
		this.itemTypes = new HashMap<>();
		
		this.loadConfig();
	}
	
	public List<Item> getItems() {
		return this.items;
	}
	
	public List<? extends Item> getItems(ItemType type) {
		return this.itemTypes.getOrDefault(type, Collections.emptyList());
	}
	
	public Item getItemByName(String name) {
		return this.getItemByName(name, Item.class);
	}
	
	public <Type extends Item> Type getItemByName(String name, Class<Type> type) {
		return this.items.stream()
			.filter(item -> item.getName().equalsIgnoreCase(name))
			.map(type::cast)
			.findFirst()
			.orElse(null);
	}
	
	@SuppressWarnings("unchecked")
	public <Type extends Item> Type getItemByName(String name, Type defaultValue) {
		return this.items.stream()
			.filter(item -> item.getName().equalsIgnoreCase(name))
			.map(item -> (Type) item)
			.findFirst()
			.orElse(defaultValue);
	}
	
	public void reloadConfig() {
		this.items.clear();
		this.itemTypes.clear();
		
		this.loadConfig();
	}
	
	public void loadConfig() {
		try (FileInputStream stream = new FileInputStream(new File("economy.json"))) {
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
						item = new Material(itemData.getString("name"), itemData.getLong("price"), itemData.getString("emote"), itemData.getBoolean("hidden"));
						
						break;
					case WOOD:
						item = new Wood(itemData.getString("name"), itemData.getLong("price"));
						
						break;
					case ENVELOPE:
						item = new Envelope(itemData.getString("name"), itemData.getLong("price"));
						
						break;
					case MINER:
						item = new Miner(itemData.getString("name"), itemData.getLong("price"), itemData.getLong("maxMaterials"), itemData.getDouble("multiplier"));
						
						break;
					case FACTORY:
						JSONObject cost = itemData.getJSONObject("cost");
						Material material = this.getItemByName(cost.getString("name"), Material.class);
						ItemStack<Material> itemStack = new ItemStack<>(material, cost.getLong("amount"));
						
						item = new Factory(itemData.getString("name"), itemStack, itemData.getLong("minYield"), itemData.getLong("maxYield"));
						
						break;
					case CRATE:
						JSONObject contentsData = itemData.getJSONObject("contents");
						Map<ItemType, Long> contents = contentsData.keySet()
							.stream()
							.collect(Collectors.toMap(ItemType::fromDataName, contentsData::getLong));
						
						item = new Crate(itemData.getString("name"), itemData.getLong("price"), itemData.getLong("points"), contents);
						
						break;
					case BOOSTER:
						item = new Booster(itemData.getString("name"), itemData.getLong("price"));
						
						break;
					case ROD:
						JSONArray rodArray = itemData.getJSONArray("craft");
						
						List<ItemStack<Material>> rodCraft = new ArrayList<>();
						for (int c = 0; c < rodArray.length(); c++) {
							JSONObject craftData = rodArray.getJSONObject(c);
							Material craftMaterial = this.getItemByName(craftData.getString("item"), Material.class);
							
							rodCraft.add(new ItemStack<>(craftMaterial, craftData.getLong("amount")));
						}
						
						Material rodRepairItem = this.getItemByName(itemData.getString("repairItem"), Material.class);
						
						item = new Rod(itemData.getString("name"), itemData.getLong("price"), itemData.getLong("durability"), rodCraft, rodRepairItem, itemData.getLong("minYield"), itemData.getLong("maxYield"));
						
						break;
					case AXE:
						JSONArray axeArray = itemData.getJSONArray("craft");
						
						List<ItemStack<Material>> axeCraft = new ArrayList<>();
						for (int c = 0; c < axeArray.length(); c++) {
							JSONObject craftData = axeArray.getJSONObject(c);
							Material craftMaterial = this.getItemByName(craftData.getString("item"), Material.class);
							
							axeCraft.add(new ItemStack<>(craftMaterial, craftData.getLong("amount")));
						}
						
						Material axeRepairItem = this.getItemByName(itemData.getString("repairItem"), Material.class);
						
						item = new Axe(itemData.getString("name"), itemData.getLong("price"), itemData.getLong("durability"), axeCraft, axeRepairItem, itemData.getLong("maxMaterials"), itemData.getDouble("multiplier"));
						
						break;
					case PICKAXE:
						JSONArray pickaxeArray = itemData.getJSONArray("craft");
						
						List<ItemStack<Material>> pickaxeCraft = new ArrayList<>();
						for (int c = 0; c < pickaxeArray.length(); c++) {
							JSONObject craftData = pickaxeArray.getJSONObject(c);
							Material craftMaterial = this.getItemByName(craftData.getString("item"), Material.class);
							
							pickaxeCraft.add(new ItemStack<>(craftMaterial, craftData.getLong("amount")));
						}
						
						Material pickaxeRepairItem = this.getItemByName(itemData.getString("repairItem"), Material.class);
						
						item = new Pickaxe(itemData.getString("name"), itemData.getLong("price"), itemData.getLong("durability"), pickaxeCraft, pickaxeRepairItem, itemData.getLong("minYield"), itemData.getLong("maxYield"), itemData.getDouble("multiplier"));
						
						break;
					default:
						item = null;
				}
				
				this.items.add(item);
				this.itemTypes.compute(type, (key, value) -> {
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
		}
	}
	
}
