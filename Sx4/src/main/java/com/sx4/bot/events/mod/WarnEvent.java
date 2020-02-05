package com.sx4.bot.events.mod;

import com.sx4.bot.entities.mod.Action;
import com.sx4.bot.entities.warn.WarnConfig;
import com.sx4.bot.entities.warn.WarnData;
import com.sx4.bot.entities.warn.WarnUser;
import com.sx4.bot.hooks.mod.ModAction;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;

public class WarnEvent extends ModActionEvent {

	private final WarnData data;
	
	public WarnEvent(Member moderator, User target, String reason, WarnData data) {
		super(moderator, target, reason, new Action(ModAction.WARN));
		
		this.data = data;
	}
	
	public WarnData getData() {
		return this.data;
	}
	
	public WarnUser getWarnUser() {
		return this.data.getUserById(this.getTarget().getIdLong());
	}
	
	public WarnConfig getWarnConfig() {
		return this.data.getConfigById(this.getWarnUser().getAmount());
	}
	
}
