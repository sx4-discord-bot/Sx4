package com.sx4.bot.games.blackjack;

import java.util.List;

import com.sx4.bot.core.Sx4Bot;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;

public class BlackJackPlayer {

	private long id;
	private Long bet = null;
	
	private List<BlackJackCard> deck;
	
	private boolean stand = false;
	
	public BlackJackPlayer(long id) {
		this.id = id;
	}
	
	public BlackJackPlayer(Member member) {
		this.id = member.getIdLong();
	}
	
	public BlackJackPlayer(Member member, long bet) {
		this.id = member.getIdLong();
		this.bet = bet;
	}
	
	public long getUserId() {
		return this.id;
	}
	
	public User getUser() {
		return Sx4Bot.getShardManager().getUserById(this.id);
	}
	
	public boolean hasBet() {
		return this.bet != null;
	}
	
	public Long getBet() {
		return this.bet;
	}
	
	public String getDeckDisplay() {
		StringBuilder display = new StringBuilder();
		for (BlackJackCard card : this.deck) {
			display.append(card.getEmote());
		}
		
		return display.toString();
	}
	
	public List<BlackJackCard> getDeck() {
		return this.deck;
	}
	
	public void addCard(BlackJackCard card) {
		this.deck.add(card);
	}
	
	public void addCards(List<BlackJackCard> card) {
		this.deck.addAll(card);
	}
	
	public int getDeckWorth() {
		int worth = 0;
		for (BlackJackCard card : this.deck) {
			worth += card.getWorth();
		}
		
		if (worth > 21) {
	        for (BlackJackCard card : this.deck) {
	            if (card.getCardType().equals(CardType.ACE)) {
	                worth -= 10;
	            }
	        }
	    }
		
		return worth;
	}
	
	public boolean isBust() {
		return this.getDeckWorth() > 21;
	}
	
	public boolean isStanding() {
		return this.stand;
	}
	
	public void stand() {
		this.stand = true;
	}
	
	public boolean isDealer() {
		return this.id == 1;
	}
	
}
