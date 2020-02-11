package com.sx4.bot.utility;

import com.sx4.bot.entities.mod.Reason;

import net.dv8tion.jda.api.entities.User;

public class ModUtility {

	public static String getAuditReason(Reason reason, User moderator) {
		return (reason == null ? "None Given" : reason.getParsed()) + " [" + moderator.getAsTag() + "]";
	}
	
}
