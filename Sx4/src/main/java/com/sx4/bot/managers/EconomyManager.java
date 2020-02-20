package com.sx4.bot.managers;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

import com.sx4.bot.entities.economy.Item;
import com.sx4.bot.entities.economy.ItemType;
import com.sx4.bot.entities.economy.Material;

public class EconomyManager {

	private static final EconomyManager INSTANCE = new EconomyManager();
	
	public static EconomyManager get() {
		return EconomyManager.INSTANCE;
	}
	
	private final List<Item> items;
	
	private EconomyManager() {
		this.items = new ArrayList<>();
		this.loadConfig();
	}
	
	public List<Item> getItems() {
		return this.items;
	}
	
	public void reloadConfig() {
		this.items.clear();
		this.loadConfig();
	}
	
	public void loadConfig() {
		try (FileInputStream stream = new FileInputStream(new File("economy.json"))) {
			JSONObject json = new JSONObject(new String(stream.readAllBytes()));
			for (ItemType type : ItemType.values()) {
				JSONArray items = json.getJSONArray(type.getDataName());
				for (int i = 0; i < items.length(); i++) {
					JSONObject item = items.getJSONObject(i);
					
					switch (type) {
						case MATERIAL:
							this.items.add(new Material(item));
					}
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
}
