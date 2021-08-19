package com.sx4.bot.entities.argument;

import com.mongodb.client.model.Projections;
import com.sx4.bot.database.mongo.MongoDatabase;
import com.sx4.bot.utility.TimeUtility;

import java.time.Duration;
import java.util.List;

public class ReminderArgument {
	
	private final long duration;
	private final String reminder;

	public ReminderArgument(long duration, String reminder) {
		this.duration = duration;
		this.reminder = reminder;
	}
	
	public long getDuration() {
		return this.duration;
	}
	
	public String getReminder() {
		return this.reminder;
	}

	public static ReminderArgument parse(MongoDatabase database, long userId, String query) {
		long seconds;
		String reminder;

		int atIndex = query.lastIndexOf(" at "), inIndex = query.lastIndexOf(" in ");
		if (atIndex == inIndex) {
			throw new IllegalArgumentException("Invalid reminder format given");
		} else if (atIndex > inIndex) {
			String defaultTimeZone = database.getUserById(userId, Projections.include("reminder.timeZone")).getEmbedded(List.of("reminder", "timeZone"), "GMT");

			Duration duration = TimeUtility.getDurationFromDateTime(query.substring(atIndex + 4).trim(), defaultTimeZone);
			if (duration.isNegative() || duration.isZero()) {
				throw new IllegalArgumentException("The date cannot be in the past");
			} else {
				seconds = duration.toSeconds();
			}

			reminder = query.substring(0, atIndex).trim();
		} else {
			Duration duration = TimeUtility.getDurationFromString(query.substring(inIndex + 4).trim());
			if (duration == null) {
				throw new IllegalArgumentException("Invalid time string given");
			} else if (duration.toSeconds() < 1) {
				throw new IllegalArgumentException("The reminder duration has to be at least 1 second long");
			} else {
				seconds = duration.toSeconds();
			}

			reminder = query.substring(0, inIndex).trim();
		}

		return new ReminderArgument(seconds, reminder);
	}
	
}
