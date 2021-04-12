package com.sx4.bot.events.mod;

import com.sx4.bot.entities.mod.Reason;
import com.sx4.bot.entities.mod.action.Action;
import com.sx4.bot.entities.mod.action.ModAction;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;

public class UnbanEvent extends ModActionEvent {

	private final long targetId;

	public UnbanEvent(Member moderator, User target, Reason reason) {
		super(moderator, target, reason, new Action(ModAction.UNMUTE));

		this.targetId = target.getIdLong();
	}

	public UnbanEvent(Member moderator, long targetId, Reason reason) {
		super(moderator, null, reason, new Action(ModAction.UNMUTE));

		this.targetId = targetId;
	}

	public long getTargetId() {
		return this.targetId;
	}
	
}
