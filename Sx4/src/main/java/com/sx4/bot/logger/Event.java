package com.sx4.bot.logger;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.stream.Collectors;

public enum Event {
	
	MESSAGE_DELETE(1, Category.MESSAGE, Category.MEMBER, Category.CHANNEL),
	MESSAGE_UPDATE(2, Category.MESSAGE, Category.MEMBER, Category.CHANNEL),
	MEMBER_JOIN(3, Category.MEMBER),
	MEMBER_ROLE_ADD(4, Category.MEMBER, Category.ROLE),
	MEMBER_ROLE_REMOVE(5, Category.MEMBER, Category.ROLE),
	MEMBER_NICKNAME_UPDATE(6, Category.MEMBER),
	MEMBER_LEAVE(7, Category.MEMBER),
	MEMBER_BANNED(8, Category.MEMBER),
	MEMBER_UNBANNED(9, Category.MEMBER),
	MEMBER_SERVER_VOICE_MUTE(10, Category.MEMBER, Category.CHANNEL),
	MEMBER_SERVER_VOICE_DEAFEN(11, Category.MEMBER, Category.CHANNEL),
	MEMBER_VOICE_JOIN(12, Category.MEMBER, Category.CHANNEL),
	MEMBER_VOICE_LEAVE(13, Category.MEMBER, Category.CHANNEL),
	MEMBER_VOICE_MOVE(14, Category.MEMBER),
	STORE_CHANNEL_DELETE(15, Category.CHANNEL),
	STORE_CHANNEL_CREATE(16, Category.CHANNEL),
	STORE_CHANNEL_NAME_UPDATE(17, Category.CHANNEL),
	VOICE_CHANNEL_DELETE(18, Category.CHANNEL),
	VOICE_CHANNEL_CREATE(19, Category.CHANNEL),
	VOICE_CHANNEL_NAME_UPDATE(20, Category.CHANNEL),
	TEXT_CHANNEL_DELETE(21, Category.CHANNEL),
	TEXT_CHANNEL_CREATE(22, Category.CHANNEL),
	TEXT_CHANNEL_NAME_UPDATE(23, Category.CHANNEL),
	CATEGORY_DELETE(24, Category.CHANNEL),
	CATEGORY_CREATE(25, Category.CHANNEL),	
	CATEGORY_NAME_UPDATE(26, Category.CHANNEL),
	ROLE_CREATE(27, Category.ROLE),
	ROLE_DELETE(28, Category.ROLE),
	ROLE_NAME_UPDATE(29, Category.ROLE),
	ROLE_PERMISSION_UPDATE(30, Category.ROLE),
	BOT_ADDED(31, Category.MEMBER),
	MEMBER_VOICE_DISCONNECT(32, Category.MEMBER, Category.CHANNEL);
	
	private static final Event[] EMPTY_EVENT = new Event[0];
	
	public static final long ALL_EVENTS = Event.getRaw(Event.values());
	
	public static final long ALL_CHANNEL_EVENTS = Event.getRaw(Arrays.stream(Event.values()).filter(Event::isChannel).collect(Collectors.toSet()));
	public static final long ALL_MEMBER_EVENTS = Event.getRaw(Arrays.stream(Event.values()).filter(Event::isMember).collect(Collectors.toSet()));
	public static final long ALL_MESSAGE_EVENTS = Event.getRaw(Arrays.stream(Event.values()).filter(Event::isMessage).collect(Collectors.toSet()));
	public static final long ALL_ROLE_EVENTS = Event.getRaw(Arrays.stream(Event.values()).filter(Event::isRole).collect(Collectors.toSet()));
	
	private Category[] categories;
	
	private int offset;
	private long raw;
	
	private Event(int offset, Category... categories) {
		this.categories = categories;
		this.offset = offset;
		this.raw = 1 << offset;
	}
	
	public Category[] getCategories() {
		return this.categories;
	}
	
	public boolean containsCategory(Category category) {
		for (Category eventCategory : this.categories) {
			if (eventCategory == category) {
				return true;
			}
		}
		
		return false;
	}
	
	public boolean isChannel() {
		return this.containsCategory(Category.CHANNEL);
	}
	
	public boolean isMember() {
		return this.containsCategory(Category.MEMBER);
	}
	
	public boolean isMessage() {
		return this.containsCategory(Category.MESSAGE);
	}
	
	public boolean isRole() {
		return this.containsCategory(Category.ROLE);
	}
	
	public int getOffset() {
		return this.offset;
	}
	
	public long getRaw() {
		return this.raw;
	}
	
	public static long getRaw(Event... events) {
		long raw = 0;
		for (Event event : events) {
			raw |= event.getRaw();
		}
		
		return raw;
	}
	
	public static long getRaw(Collection<Event> events) {
		return Event.getRaw(events.toArray(EMPTY_EVENT));
	}
	
	public static EnumSet<Event> getEvents(long eventsRaw) {
		if (eventsRaw == 0) {
			return EnumSet.noneOf(Event.class);
		} else {
			EnumSet<Event> enumSet = EnumSet.noneOf(Event.class);
			for (Event event : Event.values()) {
				if ((eventsRaw & event.getRaw()) == event.getRaw()) {
					enumSet.add(event);
				}
			}
			
			return enumSet;
		}
	}

}
