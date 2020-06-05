package com.sx4.bot.utility;

public class StringUtility {
	
	public static String title(String string) {
		String[] split = string.split(" ");
		StringBuilder newString = new StringBuilder();
		for (int i = 0; i < split.length; i++) {
			String word = split[i];
			
			newString.append(word.substring(0, 1).toUpperCase() + (word.length() == 1 ? "" : word.substring(1).toLowerCase()) + (i == split.length - 1 ? "" : " "));
		}
		
		return newString.toString();
	}

	public static String getFileExtension(String query) {
		int periodIndex = query.lastIndexOf('.');
		if (periodIndex != -1) {
			return query.substring(periodIndex + 1);
		}
		
		return null;
	}
	
}
