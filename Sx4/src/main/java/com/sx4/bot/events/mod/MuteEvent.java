package com.sx4.bot.events.mod;

import com.sx4.bot.entities.mod.Reason;
import com.sx4.bot.entities.mod.action.TimeAction;
import com.sx4.bot.hooks.mod.ModAction;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;

public class MuteEvent extends ModActionEvent {
	
	public MuteEvent(Member moderator, User target, Reason reason, long duration) {
		this(moderator, target, reason, new TimeAction(ModAction.MUTE, duration));
	}
	
	public MuteEvent(Member moderator, User target, Reason reason, TimeAction action) {
		super(moderator, target, reason, action);
	}
	
	public TimeAction getAction() {
		return (TimeAction) super.getAction();
	}
	
	public boolean hasDuration() {
		return this.getAction().hasDuration();
	}
	
	public long getDuration() {
		return this.getAction().getDuration();
	}
	
}