package com.sx4.bot.entities.reminder;

import java.util.Collections;
import java.util.List;

import org.bson.Document;
import org.bson.types.ObjectId;

public class ReminderData {

	private final List<Reminder> reminders;
	
	public ReminderData(Document data) {
		List<Document> reminders = data.getList("reminders", Document.class, Collections.emptyList());
		
		this.reminders = Reminder.fromData(reminders);
	}
	
	public ReminderData(List<Reminder> reminders) {
		this.reminders = reminders;
	}
	
	public List<Reminder> getReminders() {
		return this.reminders;
	}
	
	public Reminder getReminderById(ObjectId id) {
		return this.reminders.stream()
			.filter(reminder -> reminder.getId().equals(id))
			.findFirst()
			.orElse(null);
	}
	
}
