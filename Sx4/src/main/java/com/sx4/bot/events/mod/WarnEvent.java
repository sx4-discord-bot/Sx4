package com.sx4.bot.events.mod;

import com.sx4.bot.entities.mod.Reason;
import com.sx4.bot.entities.mod.action.WarnAction;
import com.sx4.bot.entities.mod.action.Warn;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;

public class WarnEvent extends ModActionEvent {

	private final Warn nextWarning;
	
	public WarnEvent(Member moderator, User target, Reason reason, Warn warning, Warn nextWarning) {
		super(moderator, target, reason, new WarnAction(warning));

		this.nextWarning = nextWarning;
	}
	
	public WarnAction getAction() {
		return (WarnAction) super.getAction();
	}

	public Warn getNextWarning() {
		return this.nextWarning;
	}
	
}
