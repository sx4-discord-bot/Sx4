package com.sx4.bot.utility;

import net.dv8tion.jda.api.entities.User;

public class ModUtility {

	public static String getAuditReason(String reason, User moderator) {
		return (reason == null ? "None Given" : reason) + " [" + moderator.getAsTag() + "]";
	}
	
}
