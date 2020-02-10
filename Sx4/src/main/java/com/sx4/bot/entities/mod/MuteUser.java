package com.sx4.bot.entities.mod;

import java.util.List;
import java.util.stream.Collectors;

import org.bson.Document;
import org.bson.types.ObjectId;

public class MuteUser {
	
	private final ObjectId id;
	
	private final long userId;
	private final long unmuteAt;

	public MuteUser(Document data) {
		this(data.getObjectId("id"), data.getLong("userId"), data.getLong("unmuteAt"));
	}
	
	public MuteUser(ObjectId id, long userId, long unmuteAt) {
		this.id = id;
		this.userId = userId;
		this.unmuteAt = unmuteAt;
	}
	
	public ObjectId getId() {
		return this.id;
	}
	
	public String getHex() {
		return this.id.toHexString();
	}
	
	public long getTimestamp() {
		return this.id.getTimestamp();
	}
	
	public long getUserId() {
		return this.userId;
	}
	
	public long getUnmuteAt() {
		return this.unmuteAt;
	}
	
	public static List<MuteUser> fromData(List<Document> data) {
		return data.stream().map(MuteUser::new).collect(Collectors.toList());
	}
	
}
