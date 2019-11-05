package com.sx4.bot.starboard;

import java.util.Collections;
import java.util.List;

import org.bson.Document;

import com.sx4.bot.utils.StarboardUtils;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.TextChannel;

public class Starboard {
	
	private final boolean enabled;
	private final Long channelId;
	private final List<StarboardConfiguration> configuration;
	private final List<StarboardMessage> messages;
	private final List<Long> deletedMessages;
	
	public Starboard(Document data) {
		this.enabled = data.getBoolean("enabled", false);
		this.channelId = data.getLong("channelId");
		this.configuration = StarboardConfiguration.fromRaw(data.getList("configuration", Document.class, StarboardUtils.DEFAULT_STARBOARD_CONFIGURATION));
		this.messages = StarboardMessage.fromRaw(data.getList("messages", Document.class, Collections.emptyList()));
		this.deletedMessages = data.getList("deleted", Long.class, Collections.emptyList());
	}
	
	public boolean isEnabled() {
		return this.enabled;
	}
	
	public boolean hasChannel() {
		return this.channelId != null;
	}
	
	public Long getChannelId() {
		return this.channelId;
	}
	
	public TextChannel getChannel(Guild guild) {
		return this.channelId == null ? null : guild.getTextChannelById(this.channelId);
	}
	
	public List<Long> getDeletedMessages() {
		return this.deletedMessages;
	}
	
	public boolean isDeletedMessage(long messageId) {
		return this.deletedMessages.contains(messageId);
	}
	
	public List<StarboardConfiguration> getConfiguration() {
		return this.configuration;
	}
	
	public StarboardConfiguration getConfigurationById(long id) {
		for (StarboardConfiguration star : this.configuration) {
			if (star.getId() == id) {
				return star;
			}
		}
		
		return null;
	}
	
	public List<StarboardMessage> getMessages() {
		return this.messages;
	}
	
	public StarboardMessage getMessageByOriginalId(long messageId) {
		for (StarboardMessage message : this.messages) {
			if (message.getMessageId() == messageId) {
				return message;
			}
		}
		
		return null;
	}
	
	public StarboardMessage getMessageById(long messageId) {
		for (StarboardMessage message : this.messages) {
			Long starboardId = message.getStarboardId();
			if (message.getMessageId() == messageId || (starboardId != null && starboardId == messageId)) {
				return message;
			}
		}
		
		return null;
	}

}
