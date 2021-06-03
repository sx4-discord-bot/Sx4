package com.sx4.bot.entities.economy.item;

import com.sx4.bot.managers.EconomyManager;
import org.bson.Document;

import java.util.Collections;
import java.util.List;
import java.util.Map;
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
