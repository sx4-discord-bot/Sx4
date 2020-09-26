package com.sx4.bot.utility;

public class NumberUtility {

	public static boolean isNumberUnsigned(String string) {
		for (char character : string.toCharArray()) {
			if (!Character.isDigit(character)) {
				return false;
			}
		}
		
		return true;
	}
	
	public static boolean isNumber(String string) {
		for (int i = 0; i < string.length(); i++) {
			char character = string.charAt(i);
			
			if (i == 0 && (character == '+' || character == '-')) {
				continue;
			}
			
			if (!Character.isDigit(character)) {
				return false;
			}
		}
		
		return true;
	}
	
	public static String getSuffix(int number) {
		int remainderTens = number % 100;
		if (remainderTens < 11 || remainderTens > 13) {
			int remainderOnes = number % 10;
			if (remainderOnes == 1) {
				return "st";
			} else if (remainderOnes == 2) {
				return "nd";
			} else if (remainderOnes == 3) {
				return "rd";
			}
		}
		
		return "th";
	}
	
	public static String getSuffixed(int number) {
		return number + NumberUtility.getSuffix(number);
	}
	
}
