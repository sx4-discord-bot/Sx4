package com.sx4.bot.economy.items;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.sx4.bot.economy.Item;
import com.sx4.bot.economy.ItemStack;

public class Envelope extends Item {

	public static final Envelope SHOE = new Envelope("Shoe Envelope", 1);
	public static final Envelope COAL = new Envelope("Coal Envelope", 50);
	public static final Envelope COPPER = new Envelope("Copper Envelope", 300);
	public static final Envelope BRONZE = new Envelope("Bronze Envelope", 1000);
	public static final Envelope IRON = new Envelope("Iron Envelope", 5000);
	public static final Envelope ALUMINIUM = new Envelope("Aluminium Envelope", 10000);
	public static final Envelope OIL = new Envelope("Oil Envelope", 17500);
	public static final Envelope GOLD = new Envelope("Gold Envelope", 35000);
	public static final Envelope TITANIUM = new Envelope("Titanium Envelope", 90000);
	public static final Envelope URANIUM = new Envelope("Uranium Envelope", 150000);
	public static final Envelope BITCOIN = new Envelope("Bitcoin Envelope", 275000);
	public static final Envelope PLATINUM = new Envelope("Platinum Envelope", 400000);
	public static final Envelope DIAMOND = new Envelope("Diamond Envelope", 1000000);
	
	public static final Envelope[] ALL = {SHOE, COAL, COPPER, BRONZE, IRON, ALUMINIUM, OIL, GOLD, TITANIUM, URANIUM, BITCOIN, PLATINUM, DIAMOND};  
	
	public Envelope(String name, long price) {
		super(name, price);
	}
	
	public static List<ItemStack> getOptimalEnvelopes(long money) {
		Envelope[] envelopes = Envelope.ALL.clone();
		Arrays.sort(envelopes, (a, b) -> Long.compare(b.getPrice(), a.getPrice()));
		
		List<ItemStack> returnEnvelopes = new ArrayList<>();
		for (Envelope envelope : envelopes) {
			long maxAmount = (long) Math.floor((double) money / envelope.getPrice());
			
			if (maxAmount != 0) {
				returnEnvelopes.add(new ItemStack(envelope, maxAmount));
				
				money -= maxAmount * envelope.getPrice();
			}
		}
		
		return returnEnvelopes;
	}
	
	public static Envelope getEnvelopeByName(String envelopeName) {
		envelopeName = envelopeName.toLowerCase();
			
		for (Envelope envelope : Envelope.ALL) {
			if (envelope.getName().toLowerCase().equals(envelopeName)) {
				return envelope;
			}
		}
			
		for (Envelope envelope : Envelope.ALL) {
			if (envelope.getName().toLowerCase().startsWith(envelopeName)) {
				return envelope;
			}
		}
			
		for (Envelope envelope : Envelope.ALL) {
			if (envelope.getName().toLowerCase().contains(envelopeName)) {
				return envelope;
			}
		}
			
		return null;
	}
	
}
