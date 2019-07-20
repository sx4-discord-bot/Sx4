package com.sx4.uno;

import java.util.ArrayList;
import java.util.List;

public enum UnoCard {

	RED_ZERO(Colour.RED, 0, CardType.NUMBER, "<:red_0:595959595554308110>"),
	RED_ONE(Colour.RED, 1, CardType.NUMBER, "<:red_1:595959595755765797>"),
	RED_TWO(Colour.RED, 2, CardType.NUMBER, "<:red_2:595959595986452480>"),
	RED_THREE(Colour.RED, 3, CardType.NUMBER, "<:red_3:595959595961155584>"),
	RED_FOUR(Colour.RED, 4, CardType.NUMBER, "<:red_4:595959596141772810>"),
	RED_FIVE(Colour.RED, 5, CardType.NUMBER, "<:red_5:595959596250562580>"),
	RED_SIX(Colour.RED, 6, CardType.NUMBER, "<:red_6:595959595923406855>"),
	RED_SEVEN(Colour.RED, 7, CardType.NUMBER, "<:red_7:595959596149899284>"),
	RED_EIGHT(Colour.RED, 8, CardType.NUMBER, "<:red_8:595959596208881667>"),
	RED_NINE(Colour.RED, 9, CardType.NUMBER, "<:red_9:595959596254756874>"),
	RED_PLUS_TWO(Colour.RED, 10, CardType.PLUS_TWO, "<:red_plus_two:595959596124864518>"),
	RED_REVERSE(Colour.RED, 10, CardType.REVERSE, "<:red_reverse:595959596191973376>"),
	RED_SKIP(Colour.RED, 10, CardType.SKIP, "<:red_skip:595959596179390474>"),
	
	YELLOW_ZERO(Colour.YELLOW, 0, CardType.NUMBER, "<:yellow_0:595960435128139777>"),
	YELLOW_ONE(Colour.YELLOW, 1, CardType.NUMBER, "<:yellow_1:595960435497238548>"),
	YELLOW_TWO(Colour.YELLOW, 2, CardType.NUMBER, "<:yellow_2:595960434939527169>"),
	YELLOW_THREE(Colour.YELLOW, 3, CardType.NUMBER, "<:yellow_3:595960435367346186>"),
	YELLOW_FOUR(Colour.YELLOW, 4, CardType.NUMBER, "<:yellow_4:595960435409158144>"),
	YELLOW_FIVE(Colour.YELLOW, 5, CardType.NUMBER, "<:yellow_5:595960435552026624>"),
	YELLOW_SIX(Colour.YELLOW, 6, CardType.NUMBER, "<:yellow_6:595960435249774594>"),
	YELLOW_SEVEN(Colour.YELLOW, 7, CardType.NUMBER, "<:yellow_7:595960435598032916>"),
	YELLOW_EIGHT(Colour.YELLOW, 8, CardType.NUMBER, "<:yellow_8:595960435593969674>"),
	YELLOW_NINE(Colour.YELLOW, 9, CardType.NUMBER, "<:yellow_9:595960435224739870>"),
	YELLOW_PLUS_TWO(Colour.YELLOW, 10, CardType.PLUS_TWO, "<:yellow_plus_two:595960435455426560>"),
	YELLOW_REVERSE(Colour.YELLOW, 10, CardType.REVERSE, "<:yellow_reverse:595960435514146846>"),
	YELLOW_SKIP(Colour.YELLOW, 10, CardType.SKIP, "<:yellow_skip:595960435493175306>"),
	
	GREEN_ZERO(Colour.GREEN, 0, CardType.NUMBER, "<:green_0:595959596011749394>"),
	GREEN_ONE(Colour.GREEN, 1, CardType.NUMBER, "<:green_1:595959595705303052>"),
	GREEN_TWO(Colour.GREEN, 2, CardType.NUMBER, "<:green_2:595959595717885952>"),
	GREEN_THREE(Colour.GREEN, 3, CardType.NUMBER, "<:green_3:595959596032458753>"),
	GREEN_FOUR(Colour.GREEN, 4, CardType.NUMBER, "<:green_4:595959595751440414>"),
	GREEN_FIVE(Colour.GREEN, 5, CardType.NUMBER, "<:green_5:595959595751440395>"),
	GREEN_SIX(Colour.GREEN, 6, CardType.NUMBER, "<:green_6:595959595709628427>"),
	GREEN_SEVEN(Colour.GREEN, 7, CardType.NUMBER, "<:green_7:595959595743051776>"),
	GREEN_EIGHT(Colour.GREEN, 8, CardType.NUMBER, "<:green_8:595959595797577738>"),
	GREEN_NINE(Colour.GREEN, 9, CardType.NUMBER, "<:green_9:595959595768348672>"),
	GREEN_PLUS_TWO(Colour.GREEN, 10, CardType.PLUS_TWO, "<:green_plus_two:595959597026508813>"),
	GREEN_REVERSE(Colour.GREEN, 10, CardType.REVERSE, "<:green_reverse:595959595583799318>"),
	GREEN_SKIP(Colour.GREEN, 10, CardType.SKIP, "<:green_skip:595959595843846144>"),
	
	BLUE_ZERO(Colour.BLUE, 0, CardType.NUMBER, "<:blue_0:595959595172757518>"),
	BLUE_ONE(Colour.BLUE, 1, CardType.NUMBER, "<:blue_1:595959595181277203>"),
	BLUE_TWO(Colour.BLUE, 2, CardType.NUMBER, "<:blue_2:595959595092934656>"),
	BLUE_THREE(Colour.BLUE, 3, CardType.NUMBER, "<:blue_3:595959595269226497>"),
	BLUE_FOUR(Colour.BLUE, 4, CardType.NUMBER, "<:blue_4:595959595143397416>"),
	BLUE_FIVE(Colour.BLUE, 5, CardType.NUMBER, "<:blue_5:595959594958716931>"),
	BLUE_SIX(Colour.BLUE, 6, CardType.NUMBER, "<:blue_6:595959595512365076>"),
	BLUE_SEVEN(Colour.BLUE, 7, CardType.NUMBER, "<:blue_7:595959595168563200>"),
	BLUE_EIGHT(Colour.BLUE, 8, CardType.NUMBER, "<:blue_8:595959595336466435>"),
	BLUE_NINE(Colour.BLUE, 9, CardType.NUMBER, "<:blue_9:595959595210637322>"),
	BLUE_PLUS_TWO(Colour.BLUE, 10, CardType.PLUS_TWO, "<:blue_plus_two:595959595529273345>"),
	BLUE_REVERSE(Colour.BLUE, 10, CardType.REVERSE, "<:blue_reverse:595959595672010762>"),
	BLUE_SKIP(Colour.BLUE, 10, CardType.SKIP, "<:blue_skip:595959595936251904>"),
	
	//PLUS_FOUR(Colour.BLACK, 10, CardType.PLUS_FOUR, "<:wild_pick_four:595959596359614492>"),
	//COLOUR_CHANGE(Colour.BLACK, 10, CardType.COLOUR_CHANGE, "<:wild_colour_changer:595959596150030347>"),
	
	DRAW_CARD(Colour.UNKNOWN, -1, CardType.UNKNOWN, "<:draw_card:596413651632783401>");
	
	public enum Colour {
		RED,
		YELLOW, 
		GREEN, 
		BLUE, 
		BLACK, 
		UNKNOWN;
	}
	
	private int number;
	private Colour colour;
	private CardType cardType;
	private String emote;
	
	public static final List<UnoCard> DECK = new ArrayList<>();
	static {
		for (UnoCard card : UnoCard.values()) {
			if (card.getCardType().equals(CardType.UNKNOWN)) {
				continue;
			} else if (card.getCardType().equals(CardType.PLUS_FOUR) || card.getCardType().equals(CardType.COLOUR_CHANGE)) {
				for (int i = 0; i < 4; i++) {
					DECK.add(card);
				}
			} else {
				for (int i = 0; i < 2; i++) {
					DECK.add(card);
				}
			}
		}
	}
	
	private UnoCard(Colour colour, int number, CardType cardType, String emote) {
		this.number = number;
		this.colour = colour;
		this.cardType = cardType;
		this.emote = emote;
	}
	
	public String getEmote() {
		return this.emote;
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
