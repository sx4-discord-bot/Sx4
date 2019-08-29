package com.sx4.games.uno;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import com.sx4.core.Sx4Bot;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;

public class UnoPlayer {

	private List<UnoCard> cards = new ArrayList<>();
	private long userId;
	
	public UnoPlayer(Member member) {
		this.userId = member.getIdLong();
	}
	
	public UnoPlayer(Member member, List<UnoCard> cards) {
		this.userId = member.getIdLong();
		this.cards = cards;
	}
	
	public List<UnoCard> getCards() {
		return this.cards;
	}
	
	public UnoPlayer addCards(UnoCard... cards) {
		this.cards.addAll(Arrays.asList(cards));
		
		return this;
	}
	
	public UnoPlayer addCards(Collection<UnoCard> cards) {
		this.cards.addAll(cards);
		
		return this;
	}
	
	public UnoPlayer removeCards(UnoCard... cards) {
		this.cards.removeAll(List.of(cards));
		
		return this;
	}
	
	public UnoPlayer removeCards(Collection<UnoCard> cards) {
		this.cards.removeAll(cards);
		
		return this;
	}
	
	public List<UnoCard> getPlayableCards(UnoSession unoSession) {
		List<UnoCard> playableCards = new ArrayList<>();
		for (UnoCard card : this.cards) {
			if (card.isPlayable(unoSession)) {
				playableCards.add(card);
			}
		}
		
		return playableCards;
	}
	
	public boolean canPlay(UnoSession unoSession) {
		for (UnoCard card : this.cards) {
			if (card.isPlayable(unoSession)) {
				return true;
			}
		}
		
		return false;
	}
	
	public long getUserId() {
		return this.userId;
	}
	
	public User getUser() {
		return Sx4Bot.getShardManager().getUserById(this.userId);
	}
	
}
