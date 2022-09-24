package com.sx4.bot.utility;

import java.security.SecureRandom;
import java.util.Locale;

public class RandomString extends SecureRandom {

	private static final String lower = "abcdefghijklmnopqrstuvwxyz";
	private static final String upper = lower.toUpperCase(Locale.ROOT);
	private static final String numbers = "0123456789";

	private static final char[] alphaNumeric = (lower + upper + numbers).toCharArray();

	public String nextString(int count) {
		char[] characters = new char[count];
		for (int i = 0; i < count; i++) {
			characters[i] = alphaNumeric[this.nextInt(alphaNumeric.length)];
		}

		return new String(characters);
	}

}
