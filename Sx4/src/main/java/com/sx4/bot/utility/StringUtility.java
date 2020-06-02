package com.sx4.bot.utility;

public class StringUtility {

	public static String getFileExtension(String query) {
		int periodIndex = query.lastIndexOf('.');
		if (periodIndex != -1) {
			return query.substring(periodIndex + 1);
		}
		
		return null;
	}
	
}
