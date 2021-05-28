package com.sx4.bot.entities.economy;

import java.util.Arrays;
import java.util.Random;

public enum Slot {

	DIAMOND(1),
	PLATINUM(2),
	BITCOIN(4),
	TITANIUM(7),
	OIL(12),
	GOLD(20),
	ALUMINIUM(35),
	IRON(50),
	COPPER(80),
	COAL(150),
	SHOE(250);

	public static final int TOTAL = Arrays.stream(Slot.values()).mapToInt(Slot::getChance).sum();

	private final int chance;

	private Slot(int chance) {
		this.chance = chance;
	}

	public int getChance() {
		return this.chance;
	}

	public double getMultiplier() {
		return (1 / Math.pow((double) this.chance / Slot.TOTAL, 3)) * 0.25;
	}

	public Slot getAbove() {
		Slot[] values = Slot.values();

		int index;
		for (index = 0; index < values.length; index++) {
			if (values[index] == this) {
				break;
			}
		}

		return values[index == values.length - 1 ? 0 : ++index];
	}

	public Slot getBelow() {
		Slot[] values = Slot.values();

		int index;
		for (index = 0; index < values.length; index++) {
			if (values[index] == this) {
				break;
			}
		}

		return values[index == 0 ? values.length - 1 : --index];
	}

	public static Slot[] getSlots(Random random) {
		return Slot.getSlots(random, 3);
	}

	public static Slot[] getSlots(Random random, int amount) {
		Slot[] slots = new Slot[amount];
		Amount : for (int i = 0; i < amount; i++) {
			int total = 0, randomInt = random.nextInt(Slot.TOTAL) + 1;
			for (Slot slot : Slot.values()) {
				int chance = slot.getChance();
				if (randomInt >= total && randomInt < total + chance) {
					slots[i] = slot;
					continue Amount;
				}

				total += chance;
			}
		}

		return slots;
	}

}
