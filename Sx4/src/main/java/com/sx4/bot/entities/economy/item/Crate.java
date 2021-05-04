package com.sx4.bot.entities.economy.item;

import org.bson.Document;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Crate extends Item {
	
	private final Map<ItemType, Long> contents;
	private final long credits;
	
	public Crate(Document data, Crate defaultCrate) {
		this(defaultCrate.getId(), defaultCrate.getName(), defaultCrate.getPrice(), defaultCrate.getCredits(), defaultCrate.getContents());
	}

	public Crate(int id, String name, long price, long credits, Map<ItemType, Long> contents) {
		super(id, name, price, ItemType.CRATE);
		
		this.contents = contents;
		this.credits = credits;
	}
	
	public Map<ItemType, Long> getContents() {
		return this.contents;
	}
	
	public String getContentString() {
		return this.contents.entrySet().stream()
			.map(entry -> entry.getKey().getName() + " x" + entry.getValue())
			.collect(Collectors.joining("\n"));
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
	
	public List<ItemStack<?>> open() {
		return Collections.emptyList();
	}
	
}
