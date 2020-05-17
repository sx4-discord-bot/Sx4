package com.sx4.bot.entities.economy.item;

import org.bson.Document;

public class Envelope extends Item {
	
	public Envelope(Document data, Envelope defaultEnvelope) {
		this(defaultEnvelope.getName(), defaultEnvelope.getPrice());
	}

	public Envelope(String name, long price) {
		super(name, price, ItemType.ENVELOPE);
	}
	
}
