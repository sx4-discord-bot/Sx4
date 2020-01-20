package com.sx4.bot.utils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class TimeUtils {
	
	public static final Pattern TIME_REGEX = Pattern.compile("(?:(\\d+)(?: |)(?:days|day|d)|)(?: |)(?:(\\d+)(?: |)(?:hours|hour|h)|)(?: |)(?:(\\d+)(?: |)(?:minutes|minute|mins|min|m)|)(?: |)(?:(\\d+)(?: |)(?:seconds|second|secs|sec|s)|)", Pattern.CASE_INSENSITIVE);
	public static final Pattern DATE_REGEX = Pattern.compile("(?:(\\d{1,2})(?:/|-)(\\d{1,2})(?:/|-|)((?:\\d{1,2}|))|)(?: |)(?:(\\d{1,2}):(\\d{1,2})|)(?: |)(?:([A-Za-z]+)((?:\\+|-)\\d+|)|)", Pattern.CASE_INSENSITIVE);
	
	public static int getActualDaysApart(int value) {
		return value < 0 ? 365 + value : value;
	}

	public static long convertToSeconds(String time) {
		try {
			return Long.parseLong(time);
		} catch(NumberFormatException e) {
			Matcher timeGroups = TIME_REGEX.matcher(time);
			if (!timeGroups.matches()) {
				return 0L;
			} else {
				try {
					long days = timeGroups.group(1) == null ? 0L : Long.parseLong(timeGroups.group(1));
					long hours = timeGroups.group(2) == null ? 0L : Long.parseLong(timeGroups.group(2));
					long minutes = timeGroups.group(3) == null ? 0L : Long.parseLong(timeGroups.group(3));
					long seconds = timeGroups.group(4) == null ? 0L : Long.parseLong(timeGroups.group(4));
					return (days * 86400L) + (hours * 3600L) + (minutes * 60L) + seconds; 
				} catch(NumberFormatException ex) {
					return Long.MAX_VALUE;
				}
			}
		}
	}
	
	public static long dateTimeToDuration(String date) {
		Matcher dateMatch = DATE_REGEX.matcher(date);
		if (dateMatch.matches()) {
			String timeZoneString = dateMatch.group(6) == null ? "UTC" : dateMatch.group(6);
			TimeZone timeZone = TimeZone.getTimeZone(timeZoneString);
			
			LocalDateTime now = LocalDateTime.now(timeZone.toZoneId());
			String dayGroup = dateMatch.group(1), monthGroup = dateMatch.group(2), yearGroup = dateMatch.group(3), hourGroup = dateMatch.group(4), minuteGroup = dateMatch.group(5), offsetGroup = dateMatch.group(7);
			
			int day = dayGroup == null ? now.getDayOfMonth() : Integer.parseInt(dayGroup);
			int month = monthGroup == null ? now.getMonthValue() : Integer.parseInt(monthGroup);
			int year = yearGroup == null || yearGroup.length() == 0 ? now.getYear() : Integer.parseInt(yearGroup) + ((now.getYear() / 1000) * 1000);
			int hour = hourGroup == null ? 0 : Integer.parseInt(hourGroup);
			int minute = minuteGroup == null ? 0 : Integer.parseInt(minuteGroup);
			int offset = offsetGroup == null || offsetGroup.length() == 0 ? 0 : Integer.parseInt(offsetGroup);
			
			ZonedDateTime dateTime = ZonedDateTime.of(year, month, day, hour, minute, 0, 0, timeZone.toZoneId()).minusHours(offset);
			
			Duration duration = Duration.between(now, dateTime);
			long timeTill = duration.isNegative() && dayGroup == null && monthGroup == null && yearGroup == null ? duration.plusDays(1).toSeconds() : duration.toSeconds();
			if (timeTill <= 0) {
				throw new IllegalArgumentException("You cannot set a reminder in the past :no_entry:");
			} else {
				return timeTill;
			}
		} else {
			return 0L;
		}
	}
	
	public static String toTimeString(long time, ChronoUnit from) {
		ChronoUnit[] chronoUnits = { ChronoUnit.DAYS, ChronoUnit.SECONDS, ChronoUnit.MINUTES, 
				ChronoUnit.HOURS, ChronoUnit.WEEKS, ChronoUnit.MONTHS, ChronoUnit.YEARS
		};
		if (from.getDuration().toMillis() < 1) {
			throw new IllegalArgumentException("Millis is the smallest supported unit");
		}
		
		for(ChronoUnit unit : chronoUnits) {
			if (unit.getDuration().toMillis() < 1) {
				throw new IllegalArgumentException("Millis is the smallest supported unit");
			}
		}
		
		time = (time * from.getDuration().toMillis());
		
		List<ChronoUnit> units = List.of(chronoUnits).stream()
			.distinct()
			.sorted((a, b) -> Long.compare(b.getDuration().toMillis(), a.getDuration().toMillis()))
			.collect(Collectors.toList());
		
		long[] unitValue = new long[units.size()];
		if (time > 0 && (time % units.get(units.size() - 1).getDuration().toMillis() != time)) {
			String str = "";
			for (int i = 0; i < units.size(); i++) {
				long duration = units.get(i).getDuration().toMillis();
				
				unitValue[i] = time/duration;
				time = time % duration;
				
				long value = unitValue[i];
				if (value > 0) {
					String name = units.get(i).toString().toLowerCase();
					str += (unitValue[i] + " " + name.substring(0, name.length() - 1) + (unitValue[i] != 1 ? "s" : "") + " ");
				}
			}
			
			return str.trim();
		} else {
			return "0 " + units.get(units.size() - 1).toString().toLowerCase();
		}
	}
	
	public static String getTimeFormat(long seconds) {
		long hours = seconds / 60 / 60;
		long minutes = (seconds / 60) % 60;
		seconds = seconds % 60;
		return (hours == 0 ? "" : (hours > 0 && hours < 10 ? "0" : "") + hours + ":") + ((minutes >= 0 && minutes < 10 ? "0" : "") + minutes + ":") + ((seconds >= 0 && seconds < 10 ? "0" : "") + seconds);
	}
	
}
