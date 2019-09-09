package com.sx4.bot.economy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class CraftingRecipe {
	
	private List<ItemStack> craftItems = new ArrayList<>();
	
	public CraftingRecipe(ItemStack... items) {
		for (ItemStack item : items) {
			this.craftItems.add(item);
		}
	}
	
	public CraftingRecipe(Collection<ItemStack> items) {
		this.craftItems.addAll(items);
	}
	
	public CraftingRecipe(Item item, int amount) {
		this.craftItems.add(new ItemStack(item, amount));
	}
	
	public CraftingRecipe(Item firstItem, int firstAmount, Item secondItem, int secondAmount) {
		this.craftItems.add(new ItemStack(firstItem, firstAmount));
		this.craftItems.add(new ItemStack(secondItem, secondAmount));
	}
	
	public List<ItemStack> getCraftingItems() {
		return this.craftItems;
	}
}
