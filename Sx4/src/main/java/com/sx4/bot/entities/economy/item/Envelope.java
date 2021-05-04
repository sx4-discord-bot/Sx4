package com.sx4.bot.entities.economy.item;

import org.bson.Document;

public class Envelope extends Item {
	
	public Envelope(Document data, Envelope defaultEnvelope) {
		this(defaultEnvelope.getId(), defaultEnvelope.getName(), defaultEnvelope.getPrice());
	}

	public Envelope(int id, String name, long price) {
		super(id, name, price, ItemType.ENVELOPE);
	}
	
}
