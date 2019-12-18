package com.sx4.bot.economy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.sx4.bot.economy.materials.Material;

public class CraftingRecipe {
	
	private List<ItemStack<Material>> craftMaterials = new ArrayList<>();
	
	@SuppressWarnings("unchecked")
	public CraftingRecipe(ItemStack<Material>... materials) {
		for (ItemStack<Material> material : materials) {
			this.craftMaterials.add(material);
		}
	}
	
	public CraftingRecipe(Collection<ItemStack<Material>> items) {
		this.craftMaterials.addAll(items);
	}
	
	public CraftingRecipe(Material material, int amount) {
		this.craftMaterials.add(new ItemStack<>(material, amount));
	}
	
	public CraftingRecipe(Material firstMaterial, int firstAmount, Material secondMaterial, int secondAmount) {
		this.craftMaterials.add(new ItemStack<>(firstMaterial, firstAmount));
		this.craftMaterials.add(new ItemStack<>(secondMaterial, secondAmount));
	}
	
	public List<ItemStack<Material>> getCraftingMaterials() {
		return this.craftMaterials;
	}
}
