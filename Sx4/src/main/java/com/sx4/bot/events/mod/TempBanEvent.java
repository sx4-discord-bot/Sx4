package com.sx4.bot.events.mod;

import com.sx4.bot.entities.mod.Reason;
import com.sx4.bot.entities.mod.action.ModAction;
import com.sx4.bot.entities.mod.action.TimeAction;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;

public class TempBanEvent extends BanEvent {

	public TempBanEvent(Member moderator, User target, Reason reason, boolean member, long duration) {
		super(moderator, target, reason, new TimeAction(ModAction.TEMP_BAN, duration), member);
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
