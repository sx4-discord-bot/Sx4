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

	public static String getFileExtension(String string) {
		int periodIndex = string.lastIndexOf('.');
		if (periodIndex != -1) {
			return string.substring(periodIndex + 1);
		}
		
		return null;
	}
	
	public static String substring(String string, int beginIndex, int endIndex) {
		int length = string.length();
		if (endIndex < 0) {
			endIndex = length + endIndex;
		}
		
		if (beginIndex < 0) {
			beginIndex = length + beginIndex;
		}
		
		return string.substring(beginIndex, Math.min(length, endIndex));
	}
	
	public static String substring(String string, int beginIndex) {
		return StringUtility.substring(string, beginIndex, string.length());
	}
	
}
