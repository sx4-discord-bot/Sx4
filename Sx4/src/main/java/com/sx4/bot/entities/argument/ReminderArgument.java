package com.sx4.bot.entities.argument;

import com.mongodb.client.model.Projections;
import com.sx4.bot.database.Database;
import com.sx4.bot.utility.TimeUtility;

import java.time.Duration;
import java.util.List;

public class ReminderArgument {
	
	private final long duration;
	
	private final String reminder;

	public ReminderArgument(long userId, String query) {
		int atIndex = query.lastIndexOf("at"), inIndex = query.lastIndexOf("in");
		if (atIndex == inIndex) {
			throw new IllegalArgumentException("Invalid reminder format given");
		} else if (atIndex > inIndex) {
			String defaultTimeZone = Database.get().getUserById(userId, Projections.include("reminder.timeZone")).getEmbedded(List.of("reminder", "timeZone"), "GMT");
			
			Duration duration = TimeUtility.getDurationFromDateTime(query.substring(atIndex + 2).trim(), defaultTimeZone);
			if (duration.isNegative()) {
				throw new IllegalArgumentException("The date cannot be in the past");
			} else {
				this.duration = duration.toSeconds();
			}
			
			this.reminder = query.substring(0, atIndex).trim();
		} else {
			Duration duration = TimeUtility.getDurationFromString(query.substring(inIndex + 2).trim());
			if (duration != null) {
				this.duration = duration.toSeconds();
			} else {
				throw new IllegalArgumentException("Invalid time string given");
			}
			
			this.reminder = query.substring(0, inIndex).trim();
		}
	}
	
	public long getDuration() {
		return this.duration;
	}
	
	public String getReminder() {
		return this.reminder;
	}
	
}
