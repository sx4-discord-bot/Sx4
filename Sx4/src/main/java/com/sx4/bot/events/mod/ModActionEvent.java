package com.sx4.bot.events.mod;

import com.sx4.bot.entities.mod.Reason;
import com.sx4.bot.entities.mod.action.Action;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;

public class ModActionEvent {
	
	private final Member moderator;
	private final User target;
	private final Guild guild;
	
	private final Action action;
	
	private final Reason reason;
	
	public ModActionEvent(Member moderator, User target, Reason reason, Action action) {
		this.guild = moderator.getGuild();
		this.moderator = moderator;
		this.target = target;
		this.reason = reason;
		this.action = action;
	}
	
	public Guild getGuild() {
		return this.guild;
	}
	
	public Member getModerator() {
		return this.moderator;
	}
	
	public User getTarget() {
		return this.target;
	}
	
	public Action getAction() {
		return this.action;
	}
	
	public Reason getReason() {
		return this.reason;
	}
	
	public boolean isAutomatic() {
		return this.moderator.getIdLong() == this.guild.getSelfMember().getIdLong();
	}
	
}
