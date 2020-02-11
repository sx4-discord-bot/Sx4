package com.sx4.bot.events.mod;

import com.sx4.bot.entities.mod.Reason;
import com.sx4.bot.entities.mod.WarnAction;
import com.sx4.bot.entities.warn.WarnConfig;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;

public class WarnEvent extends ModActionEvent {
	
	public WarnEvent(Member moderator, User target, Reason reason, WarnConfig warning) {
		super(moderator, target, reason, new WarnAction(warning));
	}
	
	public WarnAction getAction() {
		return (WarnAction) super.getAction();
	}
	
}
