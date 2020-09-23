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
		String suffix;
		if (remainderTens >= 11 && remainderTens <= 13) {
			suffix = "th";
		} else {
			int remainderOnes = number % 10;
			if (remainderOnes == 1) {
				suffix = "st";
			} else if (remainderOnes == 2) {
				suffix = "nd";
			} else if (remainderOnes == 3) {
				suffix = "rd";
			} else {
				suffix = "th";
			}
		}
		
		return suffix;
	}
	
	public static String getSuffixed(int number) {
		return number + NumberUtility.getSuffix(number);
	}
	
}
