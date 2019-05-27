package com.sx4.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class GeneralUtils {
	
	private static Random random = new Random();
	
	public static int getRandomNumber(int min, int max)  {
		return random.nextInt((max - min) + 1) + min;
	}
	
	public static boolean isNumber(String string) {
		char[] characterArray = string.toCharArray();
		for (int i = 0; i < characterArray.length; i++) {
			char character = characterArray[i];
			
			if (i == 0 && (character == '-' || character == '+')) {
				continue;
			}
			
			if (!Character.isDigit(character)) {
				return false;
			}
		}
		
		return true;
	}
	
	public static String getNumberSuffix(int number) {
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
		
		return number + suffix;
	}
	
	public static String getNumberSuffixRaw(int number) {
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
	
	public static String getRGB(int colourRaw) {
		return String.format("(%d, %d, %d)", (colourRaw>>16)&0xFF, (colourRaw>>8)&0xFF, colourRaw&0xFF);
	}
	
	public static String title(String text) {
		String[] splitText = text.split(" ");
		String newText = "";
		for (String word : splitText) {
			newText += word.substring(0, 1).toUpperCase() + (word.length() == 1 ? " " : word.substring(1).toLowerCase() + " ");
		}
		
		return newText;
	}
	
	public static <Type> List<Type> convertSetToList(Set<Type> set) {
		List<Type> returnList = new ArrayList<>();
		for (Type object : set) {
			returnList.add(object);
		}
		
		return returnList;
	}
	
	public static String join(List<?> list, String joinBy) {
		StringBuilder output = new StringBuilder();
		for (int i = 0; i < list.size(); i++) {
			output.append(list.get(i).toString());
				
			if (i != list.size() - 1) {
				output.append(joinBy);
			}	
		}
			
		return output.toString();
	}
	
	public static String joinGrammatical(String[] array) {
		if (array.length == 1) {
			return array[0].toString();
		} else {
			StringBuilder output = new StringBuilder();
			for (int i = 0; i < array.length; i++) {
				output.append(array[i].toString());
				
				if (i == array.length - 2) {
					output.append(" and ");
				} else if (i != array.length - 1) {
					output.append(", ");
				}
			}
			
			return output.toString();
		}
	}
	
	public static String joinGrammatical(List<?> list) {
		if (list.size() == 1) {
			return list.get(0).toString();
		} else {
			StringBuilder output = new StringBuilder();
			for (int i = 0; i < list.size(); i++) {
				output.append(list.get(i).toString());
				
				if (i == list.size() - 2) {
					output.append(" and ");
				} else if (i != list.size() - 1) {
					output.append(", ");
				}
			}
			
			return output.toString();
		}
	}
	
}
