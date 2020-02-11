package com.sx4.bot.events.mod;

import com.sx4.bot.entities.mod.Action;
import com.sx4.bot.entities.mod.Reason;
import com.sx4.bot.hooks.mod.ModAction;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;

public class UnmuteEvent extends ModActionEvent {

	public UnmuteEvent(Member moderator, User target, Reason reason) {
		super(moderator, target, reason, new Action(ModAction.UNMUTE));
	}
	
}
