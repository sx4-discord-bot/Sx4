package com.sx4.bot.events.mod;

import com.sx4.bot.entities.mod.Reason;
import com.sx4.bot.entities.mod.TimeAction;
import com.sx4.bot.hooks.mod.ModAction;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;

public class MuteExtendEvent extends MuteEvent {

	public MuteExtendEvent(Member moderator, User target, Reason reason, long duration) {
		super(moderator, target, reason, new TimeAction(ModAction.MUTE_EXTEND, duration));
	}
	
}
