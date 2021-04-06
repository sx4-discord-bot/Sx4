package com.sx4.bot.entities.management;

import net.dv8tion.jda.api.entities.Emote;
import net.dv8tion.jda.api.entities.GuildChannel;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;

public class LoggerContext {

	private long moderatorId = 0L;
	private long userId = 0L;
	private long channelId = 0L;
	private long roleId = 0L;
	private long emoteId = 0L;

	public LoggerContext() {}

	public LoggerContext setUser(long userId) {
		this.userId = userId;

		return this;
	}

	public LoggerContext setUser(User user) {
		return this.setUser(user.getIdLong());
	}

	public LoggerContext setModerator(long moderatorId) {
		this.moderatorId = moderatorId;

		return this;
	}

	public LoggerContext setModerator(User moderator) {
		return this.setModerator(moderator.getIdLong());
	}

	public LoggerContext setEmote(long emoteId) {
		this.emoteId = emoteId;

		return this;
	}

	public LoggerContext setEmote(Emote emote) {
		return this.setEmote(emote.getIdLong());
	}

	public LoggerContext setRole(long roleId) {
		this.roleId = roleId;

		return this;
	}

	public LoggerContext setRole(Role role) {
		return this.setRole(role.getIdLong());
	}

	public LoggerContext setChannel(long channelId) {
		this.channelId = channelId;

		return this;
	}

	public LoggerContext setChannel(GuildChannel channel) {
		return this.setChannel(channel.getIdLong());
	}

	public long getModeratorId() {
		return this.moderatorId;
	}

	public long getUserId() {
		return this.userId;
	}

	public long getChannelId() {
		return this.channelId;
	}

	public long getRoleId() {
		return this.roleId;
	}

	public long getEmoteId() {
		return this.emoteId;
	}

}