package com.sx4.bot.entities.economy.user;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.bson.Document;

import com.sx4.bot.database.Database;
import com.sx4.bot.entities.economy.Item;
import com.sx4.bot.entities.economy.ItemStack;
import com.sx4.bot.entities.economy.ItemType;

public class EconomyUser {

	private final long points;
	private final long balance;
	private final long winnings;
	private final long experience;
	private final int level;
	
	private final Map<ItemType, Long> limits;
	
	private final List<ItemStack<?>> items;
	
	public EconomyUser(Document data) {
		this.points = data.get("points", 0L);
		this.balance = data.get("balance", 0L);
		this.winnings = data.get("winnings", 0L);
		this.experience = data.get("experience", 0L);
		this.level = 0;
		this.limits = new HashMap<>();
		
		Document limits = data.get("limits", Database.EMPTY_DOCUMENT);
		for (ItemType type : ItemType.values()) {
			this.limits.put(type, limits.get(type.getDataName(), type.getDefaultLimit()));
		}
		
		List<Document> items = data.getList("items", Document.class, Collections.emptyList());
		this.items = ItemStack.fromData(items);
	}
	
	public EconomyUser(long points, long balance, long winnings, long experience, Map<ItemType, Long> limits, List<ItemStack<?>> items) {
		this.points = points;
		this.balance = balance;
		this.winnings = winnings;
		this.experience = experience;
		this.limits = limits;
		this.level = 0;
		this.items = items;
	}
	
	public long getPoints() {
		return this.points;
	}
	
	public long getBalance() {
		return this.balance;
	}
	
	public long getWinnings() {
		return this.winnings;
	}
	
	public long getExperience() {
		return this.experience;
	}
	
	public int getLevel() {
		return this.level;
	}
	
	public List<ItemStack<?>> getItems() {
		return this.items;
	}
	
	public List<ItemStack<?>> getItems(ItemType type) {
		return this.items.stream()
			.filter(stack -> stack.getItem().getType() == type)
			.collect(Collectors.toList());
	}
	
	public List<ItemStack<?>> getItems(int type) {
		return this.getItems(ItemType.getFromType(type));
	}
	
	public long getNetworth() {
		return this.items.stream()
			.map(ItemStack::getItem)
			.mapToLong(Item::getPrice)
			.sum() + this.balance;
	}
	
	public Map.Entry<ItemType, Long> checkLimits() {
		return this.limits.entrySet().stream()
			.filter(limit -> this.getItems(limit.getKey()).size() >= limit.getValue())
			.findFirst()
			.orElse(null);
	}
	
}
