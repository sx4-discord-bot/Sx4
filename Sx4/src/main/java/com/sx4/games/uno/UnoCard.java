package com.sx4.games.uno;

import java.util.ArrayList;
import java.util.List;

public enum UnoCard {

	RED_ZERO(Colour.RED, 0, CardType.NUMBER, "<:red_0:595959595554308110>", "Red Zero"),
	RED_ONE(Colour.RED, 1, CardType.NUMBER, "<:red_1:595959595755765797>", "Red One"),
	RED_TWO(Colour.RED, 2, CardType.NUMBER, "<:red_2:595959595986452480>", "Red Two"),
	RED_THREE(Colour.RED, 3, CardType.NUMBER, "<:red_3:595959595961155584>", "Red Three"),
	RED_FOUR(Colour.RED, 4, CardType.NUMBER, "<:red_4:595959596141772810>", "Red Four"),
	RED_FIVE(Colour.RED, 5, CardType.NUMBER, "<:red_5:595959596250562580>", "Red Five"),
	RED_SIX(Colour.RED, 6, CardType.NUMBER, "<:red_6:595959595923406855>", "Red Six"),
	RED_SEVEN(Colour.RED, 7, CardType.NUMBER, "<:red_7:595959596149899284>", "Red Seven"),
	RED_EIGHT(Colour.RED, 8, CardType.NUMBER, "<:red_8:595959596208881667>", "Red Eight"),
	RED_NINE(Colour.RED, 9, CardType.NUMBER, "<:red_9:595959596254756874>", "Red Nine"),
	RED_PLUS_TWO(Colour.RED, 10, CardType.PLUS_TWO, "<:red_plus_two:595959596124864518>", "Red Plus Two"),
	RED_REVERSE(Colour.RED, 10, CardType.REVERSE, "<:red_reverse:595959596191973376>", "Red Reverse"),
	RED_SKIP(Colour.RED, 10, CardType.SKIP, "<:red_skip:595959596179390474>", "Red Skip"),
	
	YELLOW_ZERO(Colour.YELLOW, 0, CardType.NUMBER, "<:yellow_0:595960435128139777>", "Yellow Zero"),
	YELLOW_ONE(Colour.YELLOW, 1, CardType.NUMBER, "<:yellow_1:595960435497238548>", "Yellow One"),
	YELLOW_TWO(Colour.YELLOW, 2, CardType.NUMBER, "<:yellow_2:595960434939527169>", "Yellow Two"),
	YELLOW_THREE(Colour.YELLOW, 3, CardType.NUMBER, "<:yellow_3:595960435367346186>", "Yellow Three"),
	YELLOW_FOUR(Colour.YELLOW, 4, CardType.NUMBER, "<:yellow_4:595960435409158144>", "Yellow Four"),
	YELLOW_FIVE(Colour.YELLOW, 5, CardType.NUMBER, "<:yellow_5:595960435552026624>", "Yellow Five"),
	YELLOW_SIX(Colour.YELLOW, 6, CardType.NUMBER, "<:yellow_6:595960435249774594>", "Yellow Six"),
	YELLOW_SEVEN(Colour.YELLOW, 7, CardType.NUMBER, "<:yellow_7:595960435598032916>", "Yellow Seven"),
	YELLOW_EIGHT(Colour.YELLOW, 8, CardType.NUMBER, "<:yellow_8:595960435593969674>", "Yellow Eight"),
	YELLOW_NINE(Colour.YELLOW, 9, CardType.NUMBER, "<:yellow_9:595960435224739870>", "Yellow Nine"),
	YELLOW_PLUS_TWO(Colour.YELLOW, 10, CardType.PLUS_TWO, "<:yellow_plus_two:595960435455426560>", "Yellow Plus Two"),
	YELLOW_REVERSE(Colour.YELLOW, 10, CardType.REVERSE, "<:yellow_reverse:595960435514146846>", "Yellow Reverse"),
	YELLOW_SKIP(Colour.YELLOW, 10, CardType.SKIP, "<:yellow_skip:595960435493175306>", "Yellow Skip"),
	
	GREEN_ZERO(Colour.GREEN, 0, CardType.NUMBER, "<:green_0:595959596011749394>", "Green Zero"),
	GREEN_ONE(Colour.GREEN, 1, CardType.NUMBER, "<:green_1:595959595705303052>", "Green One"),
	GREEN_TWO(Colour.GREEN, 2, CardType.NUMBER, "<:green_2:595959595717885952>", "Green Two"),
	GREEN_THREE(Colour.GREEN, 3, CardType.NUMBER, "<:green_3:595959596032458753>", "Green Three"),
	GREEN_FOUR(Colour.GREEN, 4, CardType.NUMBER, "<:green_4:595959595751440414>", "Green Four"),
	GREEN_FIVE(Colour.GREEN, 5, CardType.NUMBER, "<:green_5:595959595751440395>", "Green Five"),
	GREEN_SIX(Colour.GREEN, 6, CardType.NUMBER, "<:green_6:595959595709628427>", "Green Six"),
	GREEN_SEVEN(Colour.GREEN, 7, CardType.NUMBER, "<:green_7:595959595743051776>", "Green Seven"),
	GREEN_EIGHT(Colour.GREEN, 8, CardType.NUMBER, "<:green_8:595959595797577738>", "Green Eight"),
	GREEN_NINE(Colour.GREEN, 9, CardType.NUMBER, "<:green_9:595959595768348672>", "Green Nine"),
	GREEN_PLUS_TWO(Colour.GREEN, 10, CardType.PLUS_TWO, "<:green_plus_two:595959597026508813>", "Green Plus Two"),
	GREEN_REVERSE(Colour.GREEN, 10, CardType.REVERSE, "<:green_reverse:595959595583799318>", "Green Reverse"),
	GREEN_SKIP(Colour.GREEN, 10, CardType.SKIP, "<:green_skip:595959595843846144>", "Green Skip"),
	
	BLUE_ZERO(Colour.BLUE, 0, CardType.NUMBER, "<:blue_0:595959595172757518>", "Blue Zero"),
	BLUE_ONE(Colour.BLUE, 1, CardType.NUMBER, "<:blue_1:595959595181277203>", "Blue One"),
	BLUE_TWO(Colour.BLUE, 2, CardType.NUMBER, "<:blue_2:595959595092934656>", "Blue Two"),
	BLUE_THREE(Colour.BLUE, 3, CardType.NUMBER, "<:blue_3:595959595269226497>", "Blue Three"),
	BLUE_FOUR(Colour.BLUE, 4, CardType.NUMBER, "<:blue_4:595959595143397416>", "Blue Four"),
	BLUE_FIVE(Colour.BLUE, 5, CardType.NUMBER, "<:blue_5:595959594958716931>", "Blue Five"),
	BLUE_SIX(Colour.BLUE, 6, CardType.NUMBER, "<:blue_6:595959595512365076>", "Blue Six"),
	BLUE_SEVEN(Colour.BLUE, 7, CardType.NUMBER, "<:blue_7:595959595168563200>", "Blue Seven"),
	BLUE_EIGHT(Colour.BLUE, 8, CardType.NUMBER, "<:blue_8:595959595336466435>", "Blue Eight"),
	BLUE_NINE(Colour.BLUE, 9, CardType.NUMBER, "<:blue_9:595959595210637322>", "Blue Nine"),
	BLUE_PLUS_TWO(Colour.BLUE, 10, CardType.PLUS_TWO, "<:blue_plus_two:595959595529273345>", "Blue Plus Two"),
	BLUE_REVERSE(Colour.BLUE, 10, CardType.REVERSE, "<:blue_reverse:595959595672010762>", "Blue Reverse"),
	BLUE_SKIP(Colour.BLUE, 10, CardType.SKIP, "<:blue_skip:595959595936251904>", "Blue Skip"),
	
	PLUS_FOUR(Colour.BLACK, 10, CardType.PLUS_FOUR, "<:wild_pick_four:595959596359614492>", "Plus Four"),
	COLOUR_CHANGE(Colour.BLACK, 10, CardType.COLOUR_CHANGE, "<:wild_colour_changer:595959596150030347>", "Colour Change"),
	
	DRAW_CARD(Colour.UNKNOWN, -1, CardType.UNKNOWN, "<:draw_card:596413651632783401>", "Draw Card");
	
	public enum Colour {
		RED(true),
		YELLOW(true), 
		GREEN(true), 
		BLUE(true), 
		BLACK(false), 
		UNKNOWN(false);
		
		private boolean chooseable;
		
		private Colour(boolean chooseable) {
			this.chooseable = chooseable;
		}
		
		public boolean isChooseable() {
			return this.chooseable;
		}
		
		public static List<Colour> getChooseableColours() {
			List<Colour> chooseableColours = new ArrayList<>();
			for (Colour colour : Colour.values()) {
				if (colour.isChooseable()) {
					chooseableColours.add(colour);
				}
			}
			
			return chooseableColours;
		}
	}
	
	private int number;
	private String name;
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
	
	private UnoCard(Colour colour, int number, CardType cardType, String emote, String name) {
		this.number = number;
		this.name = name;
		this.colour = colour;
		this.cardType = cardType;
		this.emote = emote;
	}
	
	public String getEmote() {
		return this.emote;
	}
	
	public String getName() {
		return this.name;
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
	
	public boolean isPlayable(UnoSession unoSession) {
		UnoCard lastCard = unoSession.getLastCard();
		if (lastCard.equals(UnoCard.PLUS_FOUR) || lastCard.equals(UnoCard.COLOUR_CHANGE)) {
			return this.colour.equals(unoSession.getLastColour());
		} else {
			return this.colour.equals(Colour.BLACK) || this.cardType.equals(CardType.NUMBER) ? this.number == lastCard.getNumber() : this.cardType.equals(lastCard.getCardType()) || this.colour.equals(lastCard.getColour());
		}
	}
	
}
