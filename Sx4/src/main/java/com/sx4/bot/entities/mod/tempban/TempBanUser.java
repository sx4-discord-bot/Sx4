package com.sx4.bot.entities.mod.tempban;

import org.bson.Document;

import java.util.List;
import java.util.stream.Collectors;

public class TempBanUser {

	private final long unbanAt;
	private final long id;
	
	public TempBanUser(Document data) {
		this(data.getLong("id"), data.getLong("unbanAt"));
	}
	
	public TempBanUser(long userId, long unbanAt) {
		this.id = userId;
		this.unbanAt = unbanAt;
	}
	
	public long getId() {
		return this.id;
	}
	
	public long getUnbanAt() {
		return this.unbanAt;
	}
	
	public static List<TempBanUser> fromData(List<Document> data) {
		return data.stream().map(TempBanUser::new).collect(Collectors.toList());
	}
	
}
