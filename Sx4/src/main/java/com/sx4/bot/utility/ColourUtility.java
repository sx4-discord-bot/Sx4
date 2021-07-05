package com.sx4.bot.utility;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ColourUtility {
	
	private static final Pattern RGB = Pattern.compile("\\(?(\\d{1,3})[ ,]{1,2}(\\d{1,3})[ ,]{1,2}(\\d{1,3})\\)?");
	private static final Pattern HEX = Pattern.compile("#?([A-Fa-f0-9]{1,6})");

	public static int fromQuery(String query) {
		Matcher hexMatch = ColourUtility.HEX.matcher(query);
		Matcher RGBMatch = ColourUtility.RGB.matcher(query);
		if (hexMatch.matches()) {
			return Integer.parseInt(hexMatch.group(1), 16);
		} else if (RGBMatch.matches()) {
			return ColourUtility.fromRGB(Integer.parseInt(RGBMatch.group(1)), Integer.parseInt(RGBMatch.group(2)), Integer.parseInt(RGBMatch.group(3)));
		} else {
			return -1;
		}
	}
	
	public static String toHexString(int colour) {
		return String.format("%06X", 0xFFFFFF & colour);
	}
	
	public static String toRGBString(int colour) {
		return String.format("(%d, %d, %d)", (colour >> 16) & 0xFF, (colour >> 8) & 0xFF, colour & 0xFF);
	}
	
	public static int fromRGB(int red, int green, int blue) {
		return ((red & 0x0FF) << 16) | ((green & 0x0FF) << 8) | (blue & 0x0FF);
	}
	
}
