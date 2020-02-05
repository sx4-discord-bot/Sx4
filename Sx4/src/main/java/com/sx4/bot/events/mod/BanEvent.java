package com.sx4.bot.events.mod;

import com.sx4.bot.entities.mod.Action;
import com.sx4.bot.hooks.mod.ModAction;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;

public class BanEvent extends ModActionEvent {
	
	private final boolean member;

	public BanEvent(Member moderator, User target, String reason, boolean member) {
		this(moderator, target, reason, new Action(ModAction.BAN), member);
	}
	
	public BanEvent(Member moderator, User target, String reason, Action action, boolean member) {
		super(moderator, target, reason, action);
		
		this.member = member;
	}
	
	public boolean wasMember() {
		return member;
	}
	
}
