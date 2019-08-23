package com.sx4.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sx4.core.Sx4Bot;
import com.sx4.settings.Settings;

import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildChannel;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;

public class FunUtils {
	
	private static final Pattern USER_MENTION_REGEX = Pattern.compile("(" + ArgumentUtils.USER_MENTION_REGEX.pattern() + ")");
	private static final Pattern CHANNEL_MENTION_REGEX = Pattern.compile("(" + ArgumentUtils.CHANNEL_MENTION_REGEX.pattern() + ")");
	private static final Pattern ROLE_MENTION_REGEX = Pattern.compile("(" + ArgumentUtils.ROLE_MENTION_REGEX.pattern() + ")");

	public static String escapeMentions(Guild guild, String text) {
		text = text.replace("@everyone", "@\u200beveryone");
		text = text.replace("@here", "@\u200bhere");
		
		Matcher userMentionMatch = USER_MENTION_REGEX.matcher(text);
		List<String> userMentions = new ArrayList<>(), userNames = new ArrayList<>();
		while (userMentionMatch.find()) {
			userMentions.add(userMentionMatch.group(1));
			Member member = guild.getMemberById(userMentionMatch.group(2));
			userNames.add(member.getEffectiveName());
		}
		
		for (int i = 0; i < userMentions.size(); i++) {
			text = text.replace(userMentions.get(i), "@" + userNames.get(i));
		}
		
		Matcher channelMentionMatch = CHANNEL_MENTION_REGEX.matcher(text);
		List<String> channelMentions = new ArrayList<>(), channelNames = new ArrayList<>();
		while (channelMentionMatch.find()) {
			channelMentions.add(channelMentionMatch.group(1));
			GuildChannel channel = guild.getTextChannelById(channelMentionMatch.group(2));
			channelNames.add(channel.getName());
		}
		
		for (int i = 0; i < channelMentions.size(); i++) {
			text = text.replace(channelMentions.get(i), "#" + channelNames.get(i));
		}
		
		Matcher roleMentionMatch = ROLE_MENTION_REGEX.matcher(text);
		List<String> roleMentions = new ArrayList<>(), roleNames = new ArrayList<>();
		while (roleMentionMatch.find()) {
			roleMentions.add(roleMentionMatch.group(1));
			Role role = guild.getRoleById(roleMentionMatch.group(2));
			roleNames.add(role.getName());
		}
		
		for (int i = 0; i < roleMentions.size(); i++) {
			text = text.replace(roleMentions.get(i), "@" + roleNames.get(i));
		}
		
		return text;
	}
	
	public static List<String> getMemberBadges(Member member) {
		List<String> badges = new ArrayList<>();
		
		Guild supportServer = Sx4Bot.getShardManager().getGuildById(Settings.SUPPORT_SERVER_ID);
		Member guildMember = supportServer.getMember(member.getUser());
		
		if (guildMember != null) {
			if (guildMember.getRoles().contains(supportServer.getRoleById(Settings.DONATOR_ONE_ROLE_ID))) {
				badges.add("donator.png");
			}
			
			badges.add("sx4-circle.png");
		}
		
		if (Sx4Bot.getCommandListener().getDevelopers().contains(member.getIdLong())) {
			badges.add("developer.png");
		}
		
		for (Guild guild : Sx4Bot.getShardManager().getGuilds()) {
			if (guild.getOwner().equals(member)) {
				badges.add("server_owner.png");
				break;
			}
		}
		
		if (!member.getActivities().isEmpty()) {
			badges.add("playing.png");
			
			for (Activity activity : member.getActivities()) {
				if (activity.getUrl() != null) {
					badges.add("steaming.png");
				}
			}
		}
		
		return badges;
	}
	
}
