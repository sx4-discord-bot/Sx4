package com.sx4.bot.entities.economy.item;

import com.sx4.bot.managers.EconomyManager;
import org.bson.Document;

import java.time.Clock;
import java.util.Collections;
import java.util.List;

public class CooldownItemStack<Type extends Item> extends ItemStack<Type> {

	private final List<Document> resets;

	public CooldownItemStack(EconomyManager manager, Document data) {
		super(manager, data);

		this.resets = data.getList("resets", Document.class, Collections.emptyList());
	}

	public long getUsableAmount() {
		return this.getAmount() - this.getCooldownAmount();
	}

	public long getCooldownAmount() {
		return this.resets.stream()
			.filter(d -> d.getLong("time") > Clock.systemUTC().instant().getEpochSecond())
			.mapToLong(d -> d.getLong("amount"))
			.sum();
	}

	public long getNextReset() {
		long now = Clock.systemUTC().instant().getEpochSecond();

		return this.resets.stream()
			.filter(d -> d.getLong("time") > now)
			.mapToLong(d -> d.getLong("time"))
			.min()
			.orElse(-1L);
	}

	public long getTimeRemaining() {
		long nextReset = this.getNextReset();
		if (nextReset == -1) {
			return 0L;
		}

		return nextReset - Clock.systemUTC().instant().getEpochSecond();
	}

}
