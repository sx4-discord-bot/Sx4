package com.sx4.bot.utility;

import java.math.RoundingMode;
import java.text.CharacterIterator;
import java.text.DecimalFormat;
import java.text.StringCharacterIterator;

public class NumberUtility {

	public static final DecimalFormat DEFAULT_DECIMAL_FORMAT = new DecimalFormat("0.##");
	static {
		NumberUtility.DEFAULT_DECIMAL_FORMAT.setRoundingMode(RoundingMode.HALF_UP);
	}

	public static String getZeroPrefixedNumber(int value) {
		return value < 10 ? "0" + value : String.valueOf(value);
	}

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

	public static String getBytesReadable(long bytes) {
		long absoluteBytes = bytes == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(bytes);
		if (absoluteBytes < 1024) {
			return bytes + " B";
		}

		long value = absoluteBytes;

		CharacterIterator iterator = new StringCharacterIterator("KMGTPE");
		for (int i = 40; i >= 0 && absoluteBytes > 0xFFFCCCCCCCCCCCCL >> i; i -= 10) {
			value >>= 10;
			iterator.next();
		}

		value *= Long.signum(bytes);

		return String.format("%s %ciB", NumberUtility.DEFAULT_DECIMAL_FORMAT.format(value / 1024D), iterator.current());
	}

	public static String getNumberReadable(double number) {
		if (number < 1000 && number > -1000) {
			return NumberUtility.DEFAULT_DECIMAL_FORMAT.format(number);
		}

		number /= 1000;

		CharacterIterator iterator = new StringCharacterIterator("kMBTPEZY");
		while (number <= -1_000 || number >= 1_000) {
			number /= 1000;
			iterator.next();
		}

		return String.format("%s%c", NumberUtility.DEFAULT_DECIMAL_FORMAT.format(number), iterator.current());
	}
	
}
