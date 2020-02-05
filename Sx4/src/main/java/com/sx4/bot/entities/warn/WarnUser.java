package com.sx4.bot.entities.warn;

import java.util.List;
import java.util.stream.Collectors;

import org.bson.Document;

import com.sx4.bot.core.Sx4Bot;

import net.dv8tion.jda.api.entities.User;

public class WarnUser {

	private final long userId;
	private int amount;
	
	public WarnUser(Document data) {
		this(data.getLong("id"), data.getInteger("amount"));
	}
	
	public WarnUser(User user, int amount) {
		this(user.getIdLong(), amount);
	}
	
	public WarnUser(long userId, int amount) {
		this.userId = userId;
		this.amount = amount;
	}
	
	public long getUserId() {
		return this.userId;
	}
	
	public User getUser() {
		return Sx4Bot.getShardManager().getUserById(this.userId);
	}
	
	public int getAmount() {
		return this.amount;
	}
	
	public void incrementAmount() {
		this.amount++;
	}
	
	public static List<WarnUser> fromData(List<Document> data) {
		return data.stream().map(WarnUser::new).collect(Collectors.toList());
	}
	
}