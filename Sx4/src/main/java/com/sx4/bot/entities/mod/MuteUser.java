package com.sx4.bot.entities.mod;

import java.util.List;
import java.util.stream.Collectors;

import org.bson.Document;

public class MuteUser {
	
	private final long id;
	private final long unmuteAt;

	public MuteUser(Document data) {
		this(data.getLong("id"), data.getLong("unmuteAt"));
	}
	
	public MuteUser(long userId, long unmuteAt) {
		this.id = userId;
		this.unmuteAt = unmuteAt;
	}
	
	public long getId() {
		return this.id;
	}
	
	public long getUnmuteAt() {
		return this.unmuteAt;
	}
	
	public static List<MuteUser> fromData(List<Document> data) {
		return data.stream().map(MuteUser::new).collect(Collectors.toList());
	}
	
}
