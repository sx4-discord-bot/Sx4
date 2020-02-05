package com.sx4.bot.utility;

import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;

public class TimeUtility {
	
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
	
	public static String getTimeString(long duration, TimeUnit unit) {
		long seconds = unit.toSeconds(duration);
		
		StringBuilder string = new StringBuilder();
		for (int i = 0; i < TimeUtility.CHRONO_UNITS.length; i++) {
			ChronoUnit chronoUnit = TimeUtility.CHRONO_UNITS[i];
			
			long secondsInTime = chronoUnit.getDuration().getSeconds();
			int amount = (int) Math.floor((double) seconds / secondsInTime);
			
			if (amount != 0) {
				string.append(amount + " " + TimeUtility.getChronoUnitName(chronoUnit, amount != 1) + (i == TimeUtility.CHRONO_UNITS.length - 1 ? "" : " "));
				
				seconds -= amount * secondsInTime;
				if (seconds == 0) {
					break;
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
	
}
