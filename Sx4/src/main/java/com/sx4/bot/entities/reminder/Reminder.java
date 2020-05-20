package com.sx4.bot.entities.reminder;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.bson.Document;
import org.bson.types.ObjectId;

import com.sx4.bot.core.Sx4Bot;

import net.dv8tion.jda.api.entities.User;

public class Reminder {
	
	private final long userId;
	private final long duration;

	private final ObjectId id;
	
	private final OffsetDateTime createdAt;
	private final OffsetDateTime remindAt;
	
	private final String reminder;
	
	private final boolean repeat;
	
	public Reminder(long userId, Document data) {
		this(userId, data.getObjectId("id"), data.getBoolean("repeat", false), data.getLong("remindAt"), data.getLong("duration"), data.getString("reminder"));
	}
	
	public Reminder(long userId, ObjectId id, boolean repeat, long remindAt, long duration, String reminder) {
		this.userId = userId;
		this.id = id;
		this.createdAt = OffsetDateTime.ofInstant(Instant.ofEpochSecond(this.id.getTimestamp()), ZoneOffset.UTC);
		this.repeat = repeat;
		this.remindAt = OffsetDateTime.ofInstant(Instant.ofEpochSecond(remindAt), ZoneOffset.UTC);
		this.duration = duration;
		this.reminder = reminder;
	}
	
	public long getUserId() {
		return this.userId;
	}
	
	public User getUser() {
		return Sx4Bot.getShardManager().getUserById(userId);
	}
	
	public ObjectId getId() {
		return this.id;
	}
	
	public String getHex() {
		return this.id.toHexString();
	}
	
	public OffsetDateTime getTimeCreated() {
		return this.createdAt;
	}
	
	public long getDuration() {
		return this.duration;
	}
	
	public boolean isRepeated() {
		return this.repeat;
	}
	
	public Reminder extend() {
		this.remindAt.plusSeconds(this.duration);
		
		return this;
	}
	
	public OffsetDateTime getTimeReminded() {
		return this.remindAt;
	}
	
	public Duration getTimeRemaining() {
		return Duration.between(OffsetDateTime.now(ZoneOffset.UTC), this.remindAt);
	}
	
	public long getTimeRemainingSeconds() {
		return this.getTimeRemaining().toSeconds();
	}
	
	public String getReminder() {
		return this.reminder;
	}
	
}
