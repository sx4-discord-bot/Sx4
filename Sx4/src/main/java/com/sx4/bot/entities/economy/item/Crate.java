package com.sx4.bot.entities.economy.item;

import com.sx4.bot.managers.EconomyManager;
import org.bson.Document;

import java.util.*;
import java.util.stream.Collectors;

public class Crate extends Item {
	
	private final Map<ItemType, Long> contents;
	private final long credits;
	private final boolean hidden, openable;
	
	public Crate(Document data, Crate defaultCrate) {
		this(defaultCrate.getManager(), defaultCrate.getId(), defaultCrate.getName(), defaultCrate.getPrice(), defaultCrate.getCredits(), defaultCrate.isHidden(), defaultCrate.isOpenable(), defaultCrate.getContents());
	}

	public Crate(EconomyManager manager, int id, String name, long price, long credits, boolean hidden, boolean openable, Map<ItemType, Long> contents) {
		super(manager, id, name, price, ItemType.CRATE);
		
		this.contents = contents;
		this.hidden = hidden;
		this.openable = openable;
		this.credits = credits;
	}

	public boolean isOpenable() {
		return this.openable;
	}

	public boolean isHidden() {
		return this.hidden;
	}
	
	public Map<ItemType, Long> getContents() {
		return this.contents;
	}
	
	public String getContentString() {
		return this.contents.entrySet().stream()
			.map(entry -> entry.getKey().getName() + " x" + entry.getValue())
			.collect(Collectors.joining("\n"));
	}

	public long getContentTotal() {
		return this.contents.values().stream().reduce(0L, Long::sum);
	}
	
	public long getCredits() {
		return this.credits;
	}
	
	public Map.Entry<ItemType, Long> canOpen(Map<ItemType, Long> remainingLimits) {
		return this.contents.entrySet().stream()
			.filter(limit -> remainingLimits.get(limit.getKey()) < limit.getValue())
			.findFirst()
			.orElse(null);
	}
	
	public Item open() {
		List<Item> items = this.getManager().getItems().stream()
			.sorted(Comparator.comparingLong(Item::getPrice).reversed())
			.collect(Collectors.toList());

		for (Item item : items) {
			if (item instanceof Tool || item == this || (item instanceof Material && ((Material) item).isHidden())) {
				continue;
			}

			double randomDouble = this.getManager().getRandom().nextDouble();
			if (randomDouble <= Math.min(1, 1D / Math.ceil((double) (38 * item.getPrice()) / Math.pow(this.getPrice() / 10D, 1.4)))) {
				return item;
			}
		}

		return null;
	}

	public List<ItemStack<?>> newOpen() {
		long totalCount = this.getContentTotal();

		Map<Item, Long> itemMap = new HashMap<>();
		for (ItemType type : this.contents.keySet()) {
			List<Item> items = this.getManager().getItems(type.getItemClass()).stream()
				.sorted(Comparator.comparingLong(Item::getPrice).reversed())
				.collect(Collectors.toList());

			for (int i = 0; i < this.contents.get(type); i++) {
				for (Item item : items) {
					if (item == this || (item instanceof Material && ((Material) item).isHidden())) {
						continue;
					}

					double randomDouble = this.getManager().getRandom().nextDouble();
					if (randomDouble <= Math.min(1, 1D / Math.ceil((double) (38 * item.getPrice()) / Math.pow(this.getPrice() / 7D / totalCount, 1.4)))) {
						itemMap.compute(item, (key, value) -> value == null ? 1 : value + 1);
						break;
					}
				}
			}
		}

		List<ItemStack<?>> stacks = new ArrayList<>();
		itemMap.forEach((item, amount) -> stacks.add(new ItemStack<>(item, amount)));

		return stacks;
	}
	
}
