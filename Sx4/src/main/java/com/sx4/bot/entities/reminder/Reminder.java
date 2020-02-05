package com.sx4.bot.entities.reminder;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;

import org.bson.Document;
import org.bson.types.ObjectId;

public class Reminder {

	private final ObjectId id;
	private final OffsetDateTime createdAt;
	private final boolean repeat;
	private final OffsetDateTime remindAt;
	private final String reminder;
	
	public Reminder(Document data) {
		this(data.getObjectId("id"), data.getBoolean("repeat", false), data.getLong("remindAt"), data.getString("reminder"));
	}
	
	public Reminder(ObjectId id, boolean repeat, long remindAt, String reminder) {
		this.id = id;
		this.createdAt = OffsetDateTime.ofInstant(Instant.ofEpochSecond(this.id.getTimestamp()), ZoneOffset.UTC);
		this.repeat = repeat;
		this.remindAt = OffsetDateTime.ofInstant(Instant.ofEpochSecond(remindAt), ZoneOffset.UTC);
		this.reminder = reminder;
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
	
	public boolean isRepeated() {
		return this.repeat;
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
	
	public static List<Reminder> fromData(List<Document> data) {
		return data.stream().map(Reminder::new).collect(Collectors.toList());
	}
	
}
