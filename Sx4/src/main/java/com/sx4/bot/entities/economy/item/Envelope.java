package com.sx4.bot.entities.economy.item;

import com.sx4.bot.managers.EconomyManager;
import org.bson.Document;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class Envelope extends Item {
	
	public Envelope(Document data, Envelope defaultEnvelope) {
		this(defaultEnvelope.getManager(), defaultEnvelope.getId(), defaultEnvelope.getName(), defaultEnvelope.getPrice());
	}

	public Envelope(EconomyManager manager, int id, String name, long price) {
		super(manager, id, name, price, ItemType.ENVELOPE);
	}

	public static List<ItemStack<Envelope>> getOptimalEnvelopes(EconomyManager manager, long amount) {
		List<Envelope> envelopes = new ArrayList<>(manager.getItems(Envelope.class));
		envelopes.sort(Comparator.comparingLong(Item::getPrice).reversed());

		List<ItemStack<Envelope>> envelopeStacks = new ArrayList<>();
		for (Envelope envelope : envelopes) {
			long maxAmount = (long) Math.floor((double) amount / envelope.getPrice());
			if (maxAmount != 0) {
				envelopeStacks.add(new ItemStack<>(envelope, maxAmount));
				amount -= maxAmount * envelope.getPrice();
			}
		}

		return envelopeStacks;
	}
	
}
