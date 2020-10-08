package com.sx4.bot.entities.info;

public class IGDBFilter {

	public static String and(String... filters) {
		return "(" + String.join(" & ", filters) + ")";
	}

	public static String or(String... filters) {
		return "(" + String.join(" | ", filters) + ")";
	}

}
