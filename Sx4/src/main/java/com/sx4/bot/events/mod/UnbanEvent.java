package com.sx4.bot.events.mod;

import com.sx4.bot.entities.mod.Reason;
import com.sx4.bot.entities.mod.action.Action;
import com.sx4.bot.entities.mod.action.ModAction;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;

public class UnbanEvent extends ModActionEvent {

	public UnbanEvent(Member moderator, User user, Reason reason) {
		super(moderator, user, reason, new Action(ModAction.UNBAN));
	}

	public UnbanEvent(Member moderator, long targetId, Reason reason) {
		super(moderator, targetId, reason, new Action(ModAction.UNBAN));
	}
	
}
