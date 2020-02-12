package com.sx4.bot.events.mod;

import com.sx4.bot.entities.mod.Reason;
import com.sx4.bot.entities.mod.action.Action;
import com.sx4.bot.entities.mod.action.ModAction;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;

public class BanEvent extends ModActionEvent {
	
	private final boolean member;

	public BanEvent(Member moderator, User target, Reason reason, boolean member) {
		this(moderator, target, reason, new Action(ModAction.BAN), member);
	}
	
	public BanEvent(Member moderator, User target, Reason reason, Action action, boolean member) {
		super(moderator, target, reason, action);
		
		this.member = member;
	}
	
	public boolean wasMember() {
		return member;
	}
	
}
