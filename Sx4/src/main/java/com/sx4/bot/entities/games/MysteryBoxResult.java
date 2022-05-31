package com.sx4.bot.entities.games;

import com.sx4.bot.managers.MysteryBoxManager;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;

public class MysteryBoxResult {

	private final long winnings, bet;
	private final int clicks;

	private final ButtonInteractionEvent event;

	public MysteryBoxResult(MysteryBoxGame game, ButtonInteractionEvent event) {
		this.winnings = game.getWinnings();
		this.bet = game.getBet();
		this.clicks = game.getClicks();
		this.event = event;
	}

	public ButtonInteractionEvent getEvent() {
		return this.event;
	}

	public long getWinnings() {
		return this.winnings;
	}

	public long getBet() {
		return this.bet;
	}

	public int getClicks() {
		return this.clicks;
	}

	public boolean isJackpot() {
		return this.clicks == MysteryBoxManager.MONEY_COUNT;
	}

	public boolean isWon() {
		return this.winnings != 0L;
	}

}
