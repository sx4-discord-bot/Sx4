package com.sx4.bot.entities.warn;

import java.util.List;
import java.util.stream.Collectors;

import org.bson.Document;

import com.sx4.bot.core.Sx4Bot;

import net.dv8tion.jda.api.entities.User;

public class WarnUser {

	private final long id;
	private final int amount;
	private final boolean fake; 
	
	public WarnUser(Document data) {
		this(data.getLong("id"), data.getInteger("amount"), false);
	}
	
	public WarnUser(User user, int amount) {
		this(user.getIdLong(), amount, false);
	}
	
	public WarnUser(long userId, int amount) {
		this(userId, amount, false);
	}
	
	public WarnUser(long userId, int amount, boolean fake) {
		this.id = userId;
		this.amount = amount;
		this.fake = fake;
	}
	
	public long getId() {
		return this.id;
	}
	
	public User getUser() {
		return Sx4Bot.getShardManager().getUserById(this.id);
	}
	
	public int getAmount() {
		return this.amount;
	}
	
	public boolean isFake() {
		return this.fake;
	}
	
	public static List<WarnUser> fromData(List<Document> data) {
		return data.stream().map(WarnUser::new).collect(Collectors.toList());
	}
	
}