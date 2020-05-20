package com.sx4.bot.utility;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

public class TimeUtility {
	
	private static final List<String> SECONDS = List.of("s", "sec", "secs", "second", "seconds");
	private static final List<String> MINUTES = List.of("m", "min", "mins", "minite", "minutes");
	private static final List<String> HOURS = List.of("h", "hour", "hours");
	private static final List<String> DAYS = List.of("d", "day", "days");
	
	private static final ChronoUnit[] CHRONO_UNITS = {ChronoUnit.CENTURIES, ChronoUnit.DECADES, ChronoUnit.YEARS, ChronoUnit.MONTHS, ChronoUnit.WEEKS, ChronoUnit.DAYS, ChronoUnit.HOURS, ChronoUnit.MINUTES, ChronoUnit.SECONDS};
	private static final ChronoUnit[] MUSIC_CHRONO_UNITS = {ChronoUnit.HOURS, ChronoUnit.MINUTES, ChronoUnit.SECONDS};
	
	private static String getChronoUnitName(ChronoUnit chronoUnit, boolean plural) {
		String unitName = chronoUnit.name().toLowerCase();
		if (chronoUnit == ChronoUnit.CENTURIES) {
			return plural ? "centuries" : "century";
		} else if (chronoUnit == ChronoUnit.MILLENNIA) {
			return plural ? "millennia" : "millenium";
		} else {
			return unitName.substring(0, unitName.length() - 1) + (plural ? "s" : "");
		}
	}
	
	private static LocalDate parseDate(String date, String character) {
		String[] dateSplit = date.split(character);
		
		String yearString = null;
		if (dateSplit.length == 3) {
			yearString = dateSplit[2];
		}
		
		String dayString = dateSplit[0], monthString = dateSplit[1];
		
		if (!NumberUtility.isNumberUnsigned(dayString) || !NumberUtility.isNumberUnsigned(monthString)) {
			return null;
		}
		
		int day = Integer.parseInt(dayString), month = Integer.parseInt(monthString), year;
		if (yearString != null && NumberUtility.isNumberUnsigned(yearString)) {
			year = Integer.parseInt(yearString);
			
			if (yearString.length() == 2) {
				int currentYear = LocalDate.now().getYear();
				int remainder = currentYear % 100;
				year = currentYear - remainder + year;
			}
		} else {
			year = 0;
		}
		
		return LocalDate.of(year, month, day);
	}
	
	public static Duration getDurationToDateTime(String dateTime) {
		return TimeUtility.getDurationToDateTime(dateTime, "GMT");
	}
	
	public static Duration getDurationToDateTime(String dateTime, String defaultTimeZone) {
		String[] dateTimeSplit = dateTime.trim().split(" ");
		
		int day = 0, month = 0, year = 0, hour = 0, minute = 0; 
		TimeZone timeZone = TimeZone.getTimeZone(defaultTimeZone);
		for (String part : dateTimeSplit) {
			if (part.contains(":")) {
				String[] partSplit = part.split(":");
				String hourString = partSplit[0], minuteString = partSplit[1];
				
				if (!NumberUtility.isNumberUnsigned(hourString) || !NumberUtility.isNumberUnsigned(minuteString)) {
					continue;
				}
				
				int hourInt = Integer.parseInt(hourString);
				if (hourInt <= 24 && hourInt >= 0) {
					hour = hourInt == 24 ? 0 : hourInt;
				}
				
				int minuteInt = Integer.parseInt(minuteString);
				if (minuteInt <= 59 && minuteInt >= 0) {
					minute = minuteInt;
				}
			} else if (part.contains("/")) {
				LocalDate date = TimeUtility.parseDate(part, "/");
				year = date.getYear();
				month = date.getMonthValue();
				day = date.getDayOfMonth();
			} else if (part.contains("-")) {
				LocalDate date = TimeUtility.parseDate(part, "-");
				year = date.getYear();
				month = date.getMonthValue();
				day = date.getDayOfMonth();
			} else {
				timeZone = TimeZone.getTimeZone(part.toUpperCase().replace("UTC", "GMT"));
			}
		}
		
		int offsetSeconds = timeZone.getRawOffset() / 1000;
		
		ZonedDateTime now = ZonedDateTime.now(timeZone.toZoneId()).minusSeconds(offsetSeconds);
		year = year == 0 ? now.getYear() : year;
		month = month == 0 ? now.getMonthValue() : month;
		day = day == 0 ? now.getDayOfMonth() : day;
		
		return Duration.between(now, ZonedDateTime.of(year, month, day, hour, minute, 0, 0, timeZone.toZoneId()).minusSeconds(offsetSeconds));
	}
	
	public static String getTimeString(long duration, TimeUnit unit) {
		long seconds = unit.toSeconds(duration);
		
		StringBuilder string = new StringBuilder();
		for (int i = 0; i < TimeUtility.CHRONO_UNITS.length; i++) {
			ChronoUnit chronoUnit = TimeUtility.CHRONO_UNITS[i];
			
			long secondsInTime = chronoUnit.getDuration().getSeconds();
			int amount = (int) Math.floor((double) seconds / secondsInTime);
			
			if (amount != 0) {
				string.append(amount + " " + TimeUtility.getChronoUnitName(chronoUnit, amount != 1));
				
				seconds -= amount * secondsInTime;
				if (seconds == 0) {
					break;
				}
				
				if (i != CHRONO_UNITS.length - 1) {
					string.append(" ");
				}
			}
		}
		
		return string.toString();
	}
	
	public static String getTimeString(long seconds) {
		return TimeUtility.getTimeString(seconds, TimeUnit.SECONDS);
	}
	
	public static String getMusicTimeString(long seconds) {
		StringBuilder string = new StringBuilder();
		
		boolean overZero = false;
		for (int i = 0; i < TimeUtility.MUSIC_CHRONO_UNITS.length; i++) {
			ChronoUnit chronoUnit = TimeUtility.MUSIC_CHRONO_UNITS[i];
			
			long secondsInTime = chronoUnit.getDuration().getSeconds();
			int amount = (int) Math.floor((double) seconds / secondsInTime);
			
			if (!overZero) {
				overZero = amount != 0;
			} 
			
			if (overZero) {
				string.append((amount >= 0 && amount < 10 ? "0" : "") + amount + (i == TimeUtility.MUSIC_CHRONO_UNITS.length - 1 ? "" : ":"));
				
				seconds -= amount * secondsInTime;
			}
		}
		
		return string.toString();
	}
	
	private static long getActualSeconds(long time, String suffix) {
		if (TimeUtility.SECONDS.contains(suffix)) {
			return time;
		} else if (TimeUtility.MINUTES.contains(suffix)) {
			return time * 60L;
		} else if (TimeUtility.HOURS.contains(suffix)) {
			return time * 3600L;
		} else if (TimeUtility.DAYS.contains(suffix)) {
			return time * 86400L;
		}
		
		return 0;
	}
	
	public static Duration getDurationFromString(String query) {
		char[] charArray = query.trim().toCharArray();
		
		StringBuilder numberReader = new StringBuilder();
		StringBuilder unitReader = new StringBuilder();
		
		long seconds = 0;
		for (int i = 0; i < charArray.length; i++) {
			char character = charArray[i];
			
			if (Character.isDigit(character)) {
				if (unitReader.length() == 0) {
					numberReader.append(character);
				} else {
					long time = Long.parseLong(numberReader.toString());
					seconds += TimeUtility.getActualSeconds(time, unitReader.toString());
					
					numberReader = new StringBuilder(String.valueOf(character));
					unitReader = new StringBuilder();
				}
			} else if (Character.isWhitespace(character)) {
				continue;
			} else {
				if (i == 0) {
					return null;
				}
				
				unitReader.append(Character.toLowerCase(character));
				
				if (i == charArray.length - 1) {
					long time = Long.parseLong(numberReader.toString());
					seconds += TimeUtility.getActualSeconds(time, unitReader.toString());
				}
			}
		}
		
		if (seconds == 0) {
			return null;
		}
		
		return Duration.of(seconds, ChronoUnit.SECONDS);
	}
	
}
