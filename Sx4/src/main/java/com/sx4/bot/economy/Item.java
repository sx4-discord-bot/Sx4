package com.sx4.bot.economy;

import com.sx4.bot.utils.EconomyUtils;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Field;

public class Item {
	
	private String name;
	private Long price;
	
	public Item(String name, Long price) {
		this.name = name;
		this.price = price;
	}
	
	public String getName() {
		return this.name;
	}
	
	public boolean isBuyable() {
		return this.price != null;
	}

	public Long getCurrentPrice() {
		return this.price;
	}
	
	public Long getPrice() {
		return this.price;
	}
	
	public static Item getItemByName(String name) {
		name = name.toLowerCase();
		for (Item item : EconomyUtils.ALL_ITEMS) {
			if (item.getName().toLowerCase().equals(name)) {
				return item;
			}
		}
		
		return null;
	}
	
	public static void loadConfig(JSONObject json) {
		for (String key : json.keySet()) {
			Item item = Item.getItemByName(key);
			if (item == null) {
				continue;
			}
			
			JSONObject details = json.getJSONObject(key);
			
			for (String fieldName : details.keySet()) {
				Class<?> clazz = item.getClass();
				while (clazz.getSuperclass() != null) {
					for (Field field : clazz.getDeclaredFields()) {
						if (field.getName().equals(fieldName)) {
							boolean accessible = field.canAccess(item);
							
							if (!accessible) {
								field.setAccessible(true);
							}
							
							Object value = details.get(fieldName);
							try {
								if (value instanceof Integer && (field.getType() == Long.class || field.getType() == long.class)) {
									field.set(item, ((Integer) value).longValue());
								} else if (value instanceof Long && (field.getType() == Integer.class || field.getType() == int.class)) {
									field.set(item, ((Long) value).intValue());
								} else {
									field.set(item, value);
								}
							} catch (IllegalArgumentException | IllegalAccessException | JSONException e) {
								e.printStackTrace();
							}
							
							if (!accessible) {
								field.setAccessible(false);
							}
						}
					}
					
					clazz = clazz.getSuperclass();
				}
			}
		}
	}
	
	public static void loadConfig() {
		try (FileInputStream stream = new FileInputStream(new File("economyOverride.json"))) {
			Item.loadConfig(new JSONObject(new String(stream.readAllBytes())));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
}
