package uno;

public enum UnoCard {

	RED_ZERO(Colour.RED, 0, CardType.NUMBER),
	RED_ONE(Colour.RED, 1, CardType.NUMBER),
	RED_TWO(Colour.RED, 2, CardType.NUMBER),
	RED_THREE(Colour.RED, 3, CardType.NUMBER),
	RED_FOUR(Colour.RED, 4, CardType.NUMBER),
	RED_FIVE(Colour.RED, 5, CardType.NUMBER),
	RED_SIX(Colour.RED, 6, CardType.NUMBER),
	RED_SEVEN(Colour.RED, 7, CardType.NUMBER),
	RED_EIGHT(Colour.RED, 8, CardType.NUMBER),
	RED_NINE(Colour.RED, 9, CardType.NUMBER),
	RED_PLUS_TWO(Colour.RED, 10, CardType.PLUS_TWO),
	RED_REVERSE(Colour.RED, 10, CardType.REVERSE),
	RED_SKIP(Colour.RED, 10, CardType.SKIP),
	
	YELLOW_ZERO(Colour.YELLOW, 0, CardType.NUMBER),
	YELLOW_ONE(Colour.YELLOW, 1, CardType.NUMBER),
	YELLOW_TWO(Colour.YELLOW, 2, CardType.NUMBER),
	YELLOW_THREE(Colour.YELLOW, 3, CardType.NUMBER),
	YELLOW_FOUR(Colour.YELLOW, 4, CardType.NUMBER),
	YELLOW_FIVE(Colour.YELLOW, 5, CardType.NUMBER),
	YELLOW_SIX(Colour.YELLOW, 6, CardType.NUMBER),
	YELLOW_SEVEN(Colour.YELLOW, 7, CardType.NUMBER),
	YELLOW_EIGHT(Colour.YELLOW, 8, CardType.NUMBER),
	YELLOW_NINE(Colour.YELLOW, 9, CardType.NUMBER),
	YELLOW_PLUS_TWO(Colour.YELLOW, 10, CardType.PLUS_TWO),
	YELLOW_REVERSE(Colour.YELLOW, 10, CardType.REVERSE),
	YELLOW_SKIP(Colour.YELLOW, 10, CardType.SKIP),
	
	GREEN_ZERO(Colour.GREEN, 0, CardType.NUMBER),
	GREEN_ONE(Colour.GREEN, 1, CardType.NUMBER),
	GREEN_TWO(Colour.GREEN, 2, CardType.NUMBER),
	GREEN_THREE(Colour.GREEN, 3, CardType.NUMBER),
	GREEN_FOUR(Colour.GREEN, 4, CardType.NUMBER),
	GREEN_FIVE(Colour.GREEN, 5, CardType.NUMBER),
	GREEN_SIX(Colour.GREEN, 6, CardType.NUMBER),
	GREEN_SEVEN(Colour.GREEN, 7, CardType.NUMBER),
	GREEN_EIGHT(Colour.GREEN, 8, CardType.NUMBER),
	GREEN_NINE(Colour.GREEN, 9, CardType.NUMBER),
	GREEN_PLUS_TWO(Colour.GREEN, 10, CardType.PLUS_TWO),
	GREEN_REVERSE(Colour.GREEN, 10, CardType.REVERSE),
	GREEN_SKIP(Colour.GREEN, 10, CardType.SKIP),
	
	BLUE_ZERO(Colour.BLUE, 0, CardType.NUMBER),
	BLUE_ONE(Colour.BLUE, 1, CardType.NUMBER),
	BLUE_TWO(Colour.BLUE, 2, CardType.NUMBER),
	BLUE_THREE(Colour.BLUE, 3, CardType.NUMBER),
	BLUE_FOUR(Colour.BLUE, 4, CardType.NUMBER),
	BLUE_FIVE(Colour.BLUE, 5, CardType.NUMBER),
	BLUE_SIX(Colour.BLUE, 6, CardType.NUMBER),
	BLUE_SEVEN(Colour.BLUE, 7, CardType.NUMBER),
	BLUE_EIGHT(Colour.BLUE, 8, CardType.NUMBER),
	BLUE_NINE(Colour.BLUE, 9, CardType.NUMBER),
	BLUE_PLUS_TWO(Colour.BLUE, 10, CardType.PLUS_TWO),
	BLUE_REVERSE(Colour.BLUE, 10, CardType.REVERSE),
	BLUE_SKIP(Colour.BLUE, 10, CardType.SKIP),
	
	PLUS_FOUR(Colour.BLACK, 10, CardType.PLUS_FOUR),
	COLOUR_CHANGE(Colour.BLACK, 10, CardType.COLOUR_CHANGE);
	
	public enum Colour {
		RED,
		YELLOW, 
		GREEN, 
		BLUE, 
		BLACK;
	}
	
	private int number;
	private Colour colour;
	private CardType cardType;
	
	private UnoCard(Colour colour, int number, CardType cardType) {
		this.number = number;
		this.colour = colour;
		this.cardType = cardType;
	}
	
	public CardType getCardType() {
		return this.cardType;
	}
	
	public Colour getColour() {
		return this.colour;
	}
	
	public int getNumber() {
		return this.number;
	}
	
	public boolean isPlayable(UnoCard lastCard) {
		return this.colour.equals(Colour.BLACK) || this.cardType.equals(CardType.NUMBER) ? this.number == lastCard.getNumber() : this.cardType.equals(lastCard.getCardType()) || this.colour.equals(lastCard.getColour());
	}
	
}
