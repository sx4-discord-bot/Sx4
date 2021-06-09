package com.sx4.bot.entities;

public enum CompactNumber {

	TRILLION('T', 1_000_000_000_000L),
	BILLION('B', 1_000_000_000L),
	MILLION('M', 1_000_000L),
	THOUSAND('K', 1_000L);

	private final char character;
	private final long number;

	private CompactNumber(char character, long number) {
		this.character = character;
		this.number = number;
	}

	public char getCharacter() {
		return this.character;
	}

	public long getNumber() {
		return this.number;
	}

	public static String getCompactNumber(long number) {
		for (CompactNumber compact : CompactNumber.values()) {
			long compactNumber = compact.getNumber();
			if (number >= compactNumber) {
				return String.format("%.2f%c", (double) number / compactNumber, compact.getCharacter());
			}
		}

		return String.valueOf(number);
	}

}
