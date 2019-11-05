package com.sx4.bot.starboard;

import java.util.List;
import java.util.stream.Collectors;

import org.bson.Document;

import com.sx4.bot.core.Sx4Bot;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;

public class StarboardMessage {
	
	private final long messageId;
	private final long authorId;
	private final long channelId;
	private final Long starboardId;
	
	private final String content;
	private final String image;
	
	private final List<Long> stars;
	
	public StarboardMessage(Document message) {
		this.messageId = message.getLong("id");
		this.authorId = message.getLong("authorId");
		this.channelId = message.getLong("channelId");
		this.starboardId = message.getLong("starboardId");
		this.content = message.getString("content");
		this.image = message.getString("image");
		this.stars = message.getList("stars", Long.class);
	}
	
	public long getMessageId() {
		return this.messageId;
	}
	
	public long getAuthorId() {
		return this.authorId;
	}
	
	public User getAuthor() {
		return Sx4Bot.getShardManager().getUserById(this.authorId);
	}
	
	public long getChannelId() {
		return this.channelId;
	}
	
	public TextChannel getChannel(Guild guild) {
		return guild.getTextChannelById(this.channelId);
	}
	
	public boolean hasStarboard() {
		return this.starboardId != null;
	}
	
	public Long getStarboardId() {
		return this.starboardId;
	}
	
	public String getContent() {
		return this.content;
	}
	
	public String getImage() {
		return this.image;
	}
	
	public List<Long> getStars() {
		return this.stars;
	}
	
	public static List<StarboardMessage> fromRaw(List<Document> messages) {
		return messages.stream().map(message -> new StarboardMessage(message)).collect(Collectors.toList());
	}
	
}
