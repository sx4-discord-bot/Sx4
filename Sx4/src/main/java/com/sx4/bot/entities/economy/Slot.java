package com.sx4.bot.entities.economy;

import java.util.Arrays;
import java.util.Random;

public enum Slot {

	DIAMOND(1, "<:diamond:850030056251195432>"),
	PLATINUM(2, "<:platinum:850030294631448626>"),
	BITCOIN(4, "<:bitcoin:850030265144180736>"),
	TITANIUM(7, "<:titanium:850030119561199667>"),
	OIL(12, "<:oil:850030413681393694>"),
	GOLD(20, "<:gold:850030380890324994>"),
	ALUMINIUM(35, "<:aluminium:850030104125374505>"),
	IRON(50, "<:iron:850030359524409387>"),
	COPPER(80, "<:copper:850030089051963413>"),
	COAL(150, "<:coal:850030073750880277>"),
	SHOE(250, ":athletic_shoe:");

	public static final int TOTAL = Arrays.stream(Slot.values()).mapToInt(Slot::getChance).sum();
	public static final int SIZE = Slot.values().length;

	private final int chance;
	private final String emote;

	private Slot(int chance, String emote) {
		this.chance = chance;
		this.emote = emote;
	}

	public String getEmote() {
		return this.emote;
	}

	public int getChance() {
		return this.chance;
	}

	public double getMultiplier() {
		return ((1 / Math.pow((double) this.chance / Slot.TOTAL, 3)) / Slot.SIZE) * 0.6;
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
			int total = 0, randomInt = random.nextInt(Slot.TOTAL);
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
