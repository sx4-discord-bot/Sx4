package com.sx4.bot.utility;

import java.util.EnumSet;

import net.dv8tion.jda.api.entities.Message.MentionType;

public class MentionUtility {

	public static final EnumSet<MentionType> NONE = EnumSet.noneOf(MentionType.class);
	public static final EnumSet<MentionType> DEFAULT = EnumSet.of(MentionType.CHANNEL, MentionType.USER, MentionType.ROLE);
	public static final EnumSet<MentionType> ALL = EnumSet.allOf(MentionType.class);
	
}
