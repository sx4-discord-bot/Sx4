package com.sx4.bot.entities.games;

import net.dv8tion.jda.api.entities.Message;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class MysteryBoxGame {

	private final CompletableFuture<MysteryBoxResult> future;

	private int clicks = 0;
	private double multiplier = 0.2D;
	private long winnings = 0;

	private final long bet, messageId;
	private final Map<Integer, Boolean> boxes;

	public MysteryBoxGame(Message message, long bet, Map<Integer, Boolean> boxes) {
		this.future = new CompletableFuture<>();
		this.messageId = message.getIdLong();
		this.bet = bet;
		this.boxes = boxes;
	}

	public CompletableFuture<MysteryBoxResult> getFuture() {
		return this.future;
	}

	public double getMultiplier() {
		return this.multiplier;
	}

	public long getWinnings() {
		return this.winnings;
	}

	public void setWinnings(long winnings) {
		this.winnings = winnings;
	}

	public long getWinningsIncrease() {
		double multiplier = this.multiplier + this.clicks * 0.10;
		return (long) (this.bet * multiplier);
	}

	public long increaseWinnings() {
		this.multiplier += (this.clicks - 1) * 0.10;
		return this.winnings += this.bet * this.multiplier;
	}

	public long getBet() {
		return this.bet;
	}

	public long getMessageId() {
		return this.messageId;
	}

	public boolean getBox(int index) {
		return this.boxes.get(index);
	}

	public boolean getBox(String index) {
		return this.getBox(Integer.parseInt(index));
	}

	public int getClicks() {
		return this.clicks;
	}

	public int incrementClicks() {
		return ++this.clicks;
	}

	public void end(MysteryBoxResult result) {
		this.future.complete(result);
	}

}
