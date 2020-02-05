package com.sx4.bot.utility;

public class NumberUtility {

	public static boolean isNumberUnsigned(String string) {
		if (string.isEmpty()) {
			return false;
		}
		
		for (char character : string.toCharArray()) {
			if (!Character.isDigit(character)) {
				return false;
			}
		}
		
		return true;
	}
	
	public static boolean isNumber(String string) {
		if (string.isEmpty()) {
			return false;
		}
		
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
	
}
