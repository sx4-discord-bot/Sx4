package com.sx4.bot.entities.economy.item;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.bson.Document;

public class Crate extends Item {
	
	private final Map<ItemType, Long> contents;
	private final long points;
	
	public Crate(Document data, Crate defaultCrate) {
		this(defaultCrate.getName(), defaultCrate.getPrice(), defaultCrate.getPoints(), defaultCrate.getContents());
	}

	public Crate(String name, long price, long points, Map<ItemType, Long> contents) {
		super(name, price, ItemType.CRATE);
		
		this.contents = contents;
		this.points = points;
	}
	
	public Map<ItemType, Long> getContents() {
		return this.contents;
	}
	
	public String getContentString() {
		return this.contents.entrySet().stream()
			.map(entry -> entry.getKey().getName() + " x" + entry.getValue())
			.collect(Collectors.joining("\n"));
	}
	
	public long getPoints() {
		return this.points;
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
