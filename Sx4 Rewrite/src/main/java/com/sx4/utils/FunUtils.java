package com.sx4.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sx4.core.Sx4Bot;
import com.sx4.settings.Settings;

import net.dv8tion.jda.core.entities.Channel;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Member;
import net.dv8tion.jda.core.entities.Role;

public class FunUtils {
	
	private static Pattern userMentionRegex = Pattern.compile("(" + ArgumentUtils.userMentionRegex.pattern() + ")");
	private static Pattern channelMentionRegex = Pattern.compile("(" + ArgumentUtils.channelMentionRegex.pattern() + ")");
	private static Pattern roleMentionRegex = Pattern.compile("(" + ArgumentUtils.roleMentionRegex.pattern() + ")");

	public static String escapeMentions(Guild guild, String text) {
		text = text.replace("@everyone", "@\u200beveryone");
        text = text.replace("@here", "@\u200bhere");
        
        Matcher userMentionMatch = userMentionRegex.matcher(text);
        List<String> userMentions = new ArrayList<>(), userNames = new ArrayList<>();
        while (userMentionMatch.find()) {
        	userMentions.add(userMentionMatch.group(1));
        	Member member = guild.getMemberById(userMentionMatch.group(2));
        	userNames.add(member.getEffectiveName());
        }
        
        for (int i = 0; i < userMentions.size(); i++) {
        	text = text.replace(userMentions.get(i), "@" + userNames.get(i));
        }
        
        Matcher channelMentionMatch = channelMentionRegex.matcher(text);
        List<String> channelMentions = new ArrayList<>(), channelNames = new ArrayList<>();
        while (channelMentionMatch.find()) {
        	channelMentions.add(channelMentionMatch.group(1));
        	Channel channel = guild.getTextChannelById(channelMentionMatch.group(2));
        	channelNames.add(channel.getName());
        }
        
        for (int i = 0; i < channelMentions.size(); i++) {
        	text = text.replace(channelMentions.get(i), "#" + channelNames.get(i));
        }
        
        Matcher roleMentionMatch = roleMentionRegex.matcher(text);
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
		if (supportServer.isMember(member.getUser())) {
			Member guildMember = supportServer.getMember(member.getUser());
			
			if (Sx4Bot.getCommandListener().getDevelopers().contains(member.getUser().getIdLong())) {
				badges.add("developer.png");
			}
			
			if (guildMember.getRoles().contains(supportServer.getRoleById(Settings.DONATOR_ONE_ROLE_ID))) {
				badges.add("donator.png");
			}
			
			badges.add("sx4-circle.png");
		}
		
		for (Guild guild : Sx4Bot.getShardManager().getGuilds()) {
			if (guild.getOwner().equals(member)) {
				badges.add("server_owner.png");
				break;
			}
		}
		
		if (member.getGame() != null) {
			badges.add("playing.png");
			
			if (member.getGame().getUrl() != null) {
				badges.add("steaming.png");
			}
		}
		
		return badges;
	}
	
}
