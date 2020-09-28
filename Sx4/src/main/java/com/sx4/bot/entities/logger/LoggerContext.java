package com.sx4.bot.entities.logger;

import net.dv8tion.jda.api.entities.GuildChannel;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;

public class LoggerContext {

	private final User moderator;
	private final User user;
	private final GuildChannel channel;
	private final Role role;

	public LoggerContext(User user, GuildChannel channel, Role role) {
		this.user = user;
		this.channel = channel;
		this.role = role;
		this.moderator = null;
	}

	public LoggerContext(User user, GuildChannel channel, Role role, User moderator) {
		this.user = user;
		this.channel = channel;
		this.role = role;
		this.moderator = moderator;
	}

	public boolean hasUser() {
		return this.user != null;
	}

	public User getUser() {
		return this.user;
	}

	public boolean hasModerator() {
		return this.moderator != null;
	}

	public User getModerator() {
		return this.moderator;
	}

	public boolean hasChannel() {
		return this.channel != null;
	}

	public GuildChannel getChannel() {
		return this.channel;
	}

	public boolean hasRole() {
		return this.role != null;
	}

	public Role getRole() {
		return this.role;
	}

}