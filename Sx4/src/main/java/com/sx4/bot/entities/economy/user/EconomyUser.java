package com.sx4.bot.entities.economy.user;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.bson.Document;

import com.sx4.bot.database.Database;
import com.sx4.bot.entities.economy.item.Item;
import com.sx4.bot.entities.economy.item.ItemStack;
import com.sx4.bot.entities.economy.item.ItemType;

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
	
	public void addItems(List<ItemStack<?>> items) {
		items.forEach(this::addItem);
	}
	
	public void addItem(ItemStack<?> item) {
		ItemStack<?> itemStack = this.items.stream()
			.filter(item::equalsItem)
			.findFirst()
			.orElse(null);
		
		if (itemStack == null) {
			this.items.add(item);
		} else {
			itemStack.addAmount(item.getAmount());
		}
	}
	
	public void removeItems(List<ItemStack<?>> items) {
		items.forEach(this::removeItem);
	}
	
	public void removeItem(ItemStack<?> item) {
		ItemStack<?> itemStack = this.items.stream()
			.filter(item::equalsItem)
			.findFirst()
			.orElse(null);
		
		if (itemStack == null) {
			throw new IllegalArgumentException("That user doesn't have `" + item.getItem().getName() + "`");
		}
		
		long newAmount = itemStack.getAmount() - item.getAmount();
		if (newAmount == 0) {
			this.items.remove(itemStack);
		} else {
			itemStack.removeAmount(item.getAmount());
		}
	}
	
	public List<ItemStack<?>> getItems(ItemType type) {
		return this.items.stream()
			.filter(stack -> stack.getItem().getType() == type)
			.collect(Collectors.toList());
	}
	
	public List<ItemStack<?>> getItems(int type) {
		return this.getItems(ItemType.getFromType(type));
	}
	
	public <Type extends Item> Type getFirstItem(Class<Type> clazz) {
		return this.items.stream()
			.map(ItemStack::getItem)
			.filter(item -> item.getClass() == clazz)
			.map(clazz::cast)
			.findFirst()
			.orElse(null);
	}
	
	public boolean hasItemType(ItemType type) {
		return this.items.stream().anyMatch(stack -> stack.getItem().getType() == type);
	}
	
	public boolean hasItemType(int type) {
		return this.hasItemType(ItemType.getFromType(type));
	}
	
	public long sumItems() {
		return this.items.stream()
			.mapToLong(ItemStack::getAmount)
			.sum();
	}
	
	public long sumItems(ItemType type) {
		return this.items.stream()
			.filter(stack -> stack.getItem().getType() == type)
			.mapToLong(ItemStack::getAmount)
			.sum();
	}
	
	public long sumItems(int type) {
		return this.sumItems(ItemType.getFromType(type));
	}
	
	public long getNetworth() {
		return this.items.stream()
			.map(ItemStack::getItem)
			.filter(Item::isBuyable)
			.mapToLong(Item::getPrice)
			.sum() + this.balance;
	}
	
	public Map<ItemType, Long> getLimits() {
		return this.limits;
	}
	
	public Map<ItemType, Long> getRemainingLimits() {
		return this.limits.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, limit -> limit.getValue() - this.sumItems(limit.getKey())));
	}
	
	public void updateLimit(ItemType type, long limit) {
		this.limits.put(type, limit);
	}
	
	public Map.Entry<ItemType, Long> checkLimits() {
		return this.limits.entrySet().stream()
			.filter(limit -> this.sumItems(limit.getKey()) >= limit.getValue())
			.findFirst()
			.orElse(null);
	}
	
	public Long checkLimit(ItemType type) {
		long limit = this.limits.get(type);
		if (this.sumItems(type) >= limit) {
			return limit;
		}
		
		return null;
	}
	
}
