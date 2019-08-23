package com.sx4.games.blackjack;

import java.util.Arrays;
import java.util.List;

public enum BlackJackCard {
	
	TWO_OF_DIAMONDS(Suit.DIAMOND, 2, CardType.NUMBER, "", "Two of Diamonds"),
	THREE_OF_DIAMONDS(Suit.DIAMOND, 3, CardType.NUMBER, "", "Three of Diamonds"),
	FOUR_OF_DIAMONDS(Suit.DIAMOND, 4, CardType.NUMBER, "", "Four of Diamonds"),
	FIVE_OF_DIAMONDS(Suit.DIAMOND, 5, CardType.NUMBER, "", "Five of Diamonds"),
	SIX_OF_DIAMONDS(Suit.DIAMOND, 6, CardType.NUMBER, "", "Six of Diamonds"), 
	SEVEN_OF_DIAMONDS(Suit.DIAMOND, 7, CardType.NUMBER, "", "Seven of Diamonds"),
	EIGHT_OF_DIAMONDS(Suit.DIAMOND, 8, CardType.NUMBER, "", "Eight of Diamonds"),
	NINE_OF_DIAMONDS(Suit.DIAMOND, 9, CardType.NUMBER, "", "Nine of Diamonds"),
	TEN_OF_DIAMONDS(Suit.DIAMOND, 10, CardType.NUMBER, "", "Ten of Diamonds"),
	JACK_OF_DIAMONDS(Suit.DIAMOND, 10, CardType.JACK, "", "Jack of Diamonds"),
	QUEEN_OF_DIAMONDS(Suit.DIAMOND, 10, CardType.QUEEN, "", "Queen of Diamonds"),
	KING_OF_DIAMONDS(Suit.DIAMOND, 10, CardType.KING, "", "King of Diamonds"),
	ACE_OF_DIAMONDS(Suit.DIAMOND, 11, CardType.ACE, "", "Ace of Diamonds"),
	
	TWO_OF_SPADES(Suit.SPADE, 2, CardType.NUMBER, "", "Two of Spades"),
	THREE_OF_SPADES(Suit.SPADE, 3, CardType.NUMBER, "", "Three of Spades"),
	FOUR_OF_SPADES(Suit.SPADE, 4, CardType.NUMBER, "", "Four of Spades"),
	FIVE_OF_SPADES(Suit.SPADE, 5, CardType.NUMBER, "", "Five of Spades"),
	SIX_OF_SPADES(Suit.SPADE, 6, CardType.NUMBER, "", "Six of Spades"), 
	SEVEN_OF_SPADES(Suit.SPADE, 7, CardType.NUMBER, "", "Seven of Spades"),
	EIGHT_OF_SPADES(Suit.SPADE, 8, CardType.NUMBER, "", "Eight of Spades"),
	NINE_OF_SPADES(Suit.SPADE, 9, CardType.NUMBER, "", "Nine of Spades"),
	TEN_OF_SPADES(Suit.SPADE, 10, CardType.NUMBER, "", "Ten of Spades"),
	JACK_OF_SPADES(Suit.SPADE, 10, CardType.JACK, "", "Jack of Spades"),
	QUEEN_OF_SPADES(Suit.SPADE, 10, CardType.QUEEN, "", "Queen of Spades"),
	KING_OF_SPADES(Suit.SPADE, 10, CardType.KING, "", "King of Spades"),
	ACE_OF_SPADES(Suit.SPADE, 11, CardType.ACE, "", "Ace of Spades"),
	
	TWO_OF_CLUBS(Suit.CLUB, 2, CardType.NUMBER, "", "Two of Clubs"),
	THREE_OF_CLUBS(Suit.CLUB, 3, CardType.NUMBER, "", "Three of Clubs"),
	FOUR_OF_CLUBS(Suit.CLUB, 4, CardType.NUMBER, "", "Four of Clubs"),
	FIVE_OF_CLUBS(Suit.CLUB, 5, CardType.NUMBER, "", "Five of Clubs"),
	SIX_OF_CLUBS(Suit.CLUB, 6, CardType.NUMBER, "", "Six of Clubs"), 
	SEVEN_OF_CLUBS(Suit.CLUB, 7, CardType.NUMBER, "", "Seven of Clubs"),
	EIGHT_OF_CLUBS(Suit.CLUB, 8, CardType.NUMBER, "", "Eight of Clubs"),
	NINE_OF_CLUBS(Suit.CLUB, 9, CardType.NUMBER, "", "Nine of Clubs"),
	TEN_OF_CLUBS(Suit.CLUB, 10, CardType.NUMBER, "", "Ten of Clubs"),
	JACK_OF_CLUBS(Suit.CLUB, 10, CardType.JACK, "", "Jack of Clubs"),
	QUEEN_OF_CLUBS(Suit.CLUB, 10, CardType.QUEEN, "", "Queen of Clubs"),
	KING_OF_CLUBS(Suit.CLUB, 10, CardType.KING, "", "King of Clubs"),
	ACE_OF_CLUBS(Suit.CLUB, 11, CardType.ACE, "", "Ace of Clubs"),
	
	TWO_OF_HEARTS(Suit.HEART, 2, CardType.NUMBER, "", "Two of Hearts"),
	THREE_OF_HEARTS(Suit.HEART, 3, CardType.NUMBER, "", "Three of Hearts"),
	FOUR_OF_HEARTS(Suit.HEART, 4, CardType.NUMBER, "", "Four of Hearts"),
	FIVE_OF_HEARTS(Suit.HEART, 5, CardType.NUMBER, "", "Five of Hearts"),
	SIX_OF_HEARTS(Suit.HEART, 6, CardType.NUMBER, "", "Six of Hearts"), 
	SEVEN_OF_HEARTS(Suit.HEART, 7, CardType.NUMBER, "", "Seven of Hearts"),
	EIGHT_OF_HEARTS(Suit.HEART, 8, CardType.NUMBER, "", "Eight of Hearts"),
	NINE_OF_HEARTS(Suit.HEART, 9, CardType.NUMBER, "", "Nine of Hearts"),
	TEN_OF_HEARTS(Suit.HEART, 10, CardType.NUMBER, "", "Ten of Hearts"),
	JACK_OF_HEARTS(Suit.HEART, 10, CardType.JACK, "", "Jack of Hearts"),
	QUEEN_OF_HEARTS(Suit.HEART, 10, CardType.QUEEN, "", "Queen of Hearts"),
	KING_OF_HEARTS(Suit.HEART, 10, CardType.KING, "", "King of Hearts"),
	ACE_OF_HEARTS(Suit.HEART, 11, CardType.ACE, "", "Ace of Hearts");
	
	public static final List<BlackJackCard> CARDS = Arrays.asList(BlackJackCard.values());
	
	private Suit suit;
	private int worth;
	private CardType cardType;
	private String emote;
	private String name;
	
	private BlackJackCard(Suit suit, int worth, CardType cardType, String emote, String name) {
		this.suit = suit;
		this.worth = worth;
		this.cardType = cardType;
		this.emote = emote;
		this.name = name;
	}
	
	public Suit getSuit() {
		return this.suit;
	}
	
	public int getWorth() {
		return this.worth;
	}
	
	public CardType getCardType() {
		return this.cardType;
	}
	
	public String getEmote() {
		return this.emote;
	}
	
	public String getName() {
		return this.name;
	}
	
}
