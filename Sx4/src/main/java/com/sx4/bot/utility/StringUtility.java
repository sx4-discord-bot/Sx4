package com.sx4.bot.utility;

import java.util.List;
import java.util.StringJoiner;
import java.util.function.Function;

public class StringUtility {

	public static int getScore(String first, String second) {
		int secondLength = second.length(), firstLength = first.length();
		int maxLength = Math.max(firstLength, secondLength);
		double score = 0, maxScore = 0;
		Char : for (int s = 0; s < maxLength; s++) {
			int max = Math.max(firstLength - s - 1, s);
			if (s >= secondLength) {
				// Increase score based on remaining missing characters multiplied by the complement of the match percentage with a 0.5 offset up to this point
				int maxRemaining = max * (maxLength - secondLength);
				score += maxRemaining * (1.5D - (maxScore - score) / maxScore);
				maxScore += maxRemaining;

				break;
			}

			maxScore += max;

			char secondChar = Character.toLowerCase(second.charAt(s));
			if (s < firstLength) {
				for (int f = s; f < firstLength; f++) {
					char firstChar = Character.toLowerCase(first.charAt(f));
					if (firstChar == secondChar) {
						score += f - s;
						continue Char;
					}
				}
			}

			for (int f = Math.min(s, firstLength) - 1; f >= 0; f--) {
				char firstChar = Character.toLowerCase(first.charAt(f));
				if (firstChar == secondChar) {
					score += s - f;
					continue Char;
				}
			}

			score += max;
		}

		return (int) (((maxScore - score) / maxScore) * 100D);
	}

	public static boolean isNotEqual(String string, char firstChar, char secondChar) {
		return StringUtility.isNotEqual(string, firstChar, secondChar, false);
	}

	public static boolean isNotEqual(String string, char firstChar, char secondChar, boolean ordered) {
		int first = 0, second = 0;
		for (int i = 0; i < string.length(); i++) {
			char character = string.charAt(i), characterBefore = string.charAt(Math.max(0, i - 1));
			if (character == firstChar && characterBefore != '\\') {
				first++;
			} else if (character == secondChar && characterBefore != '\\') {
				second++;
			}

			if (ordered && second > first) {
				return true;
			}
		}

		return first != second;
	}
	
	public static String title(String string) {
		String[] split = string.split(" ");
		StringBuilder newString = new StringBuilder();
		for (int i = 0; i < split.length; i++) {
			String word = split[i];
			
			newString.append(word.substring(0, 1).toUpperCase()).append(word.length() == 1 ? "" : word.substring(1).toLowerCase()).append(i == split.length - 1 ? "" : " ");
		}
		
		return newString.toString();
	}

	public static String removeAfter(String string, char... characters) {
		for (int i = 0, index; i < characters.length; i++) {
			index = string.lastIndexOf(characters[i]);
			if (index != -1) {
				string = string.substring(0, index);
				break;
			}
		}

		return string;
	}

	public static String getFileName(String string) {
		string = string.substring(8);

		int index = string.lastIndexOf('/');
		if (index == -1) {
			return null;
		}

		String name = StringUtility.removeAfter(string.substring(index + 1), '.', '?', '#');

		return name.isBlank() ? null : name;
	}

	public static String getFileExtension(String string) {
		int extension = string.lastIndexOf('.');
		if (extension != -1) {
			return StringUtility.removeAfter(string.substring(extension + 1), '?', '#');
		}
		
		return null;
	}

	public static String limit(String string, int maxLength, String suffix) {
		return string.length() <= maxLength ? string : string.substring(0, maxLength - suffix.length()) + suffix;
	}

	public static String limit(String string, int maxLength) {
		return StringUtility.limit(string, maxLength, "");
	}
	
	public static String substring(String string, int beginIndex, int endIndex) {
		int length = string.length();
		if (endIndex < 0) {
			endIndex += length;
		}
		
		if (beginIndex < 0) {
			beginIndex += length;
		}
		
		return string.substring(Math.min(length, Math.max(0, beginIndex)), Math.min(length, endIndex));
	}
	
	public static String substring(String string, int beginIndex) {
		return StringUtility.substring(string, beginIndex, string.length());
	}

	public static String joinLimited(String delimiter, List<String> strings, int maxLength) {
		return StringUtility.joinLimited(delimiter, strings, Function.identity(), maxLength);
	}

	public static <Type> String joinLimited(String delimiter, List<Type> objects, Function<Type, String> function, int maxLength) {
		int size = objects.size();

		StringJoiner joiner = new StringJoiner(delimiter);
		for (int i = 0; i < size; i++) {
			String suffix = " and " + (size - i) + " more", string = function.apply(objects.get(i));
			int length = string.length() + joiner.length() + delimiter.length();
			if (length > maxLength - (i == size - 1 ? 0 : suffix.length())) {
				return joiner + suffix;
			}

			joiner.add(string);
		}

		return joiner.toString();
	}
	
}
