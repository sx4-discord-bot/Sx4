package com.sx4.bot.utils;

import com.sx4.bot.core.Sx4Bot;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.Message.MentionType;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.regex.Matcher;

public class ImageUtils {

	public static JSONObject getMentions(Guild guild, String text) {
		Matcher userMention = MentionType.USER.getPattern().matcher(text); 
		Matcher channelMention = MentionType.CHANNEL.getPattern().matcher(text);
		Matcher roleMention = MentionType.ROLE.getPattern().matcher(text);
		Matcher emoteMention = MentionType.EMOTE.getPattern().matcher(text);
		
		JSONObject users = new JSONObject();
		while (userMention.find()) {
			String id = userMention.group(1);
			
			Member member;
			try {
				member = guild.getMemberById(id);
			} catch (NumberFormatException e) {
				continue;
			}
			
			User user = null;
			if (member == null) {
				try {
					user = Sx4Bot.getShardManager().getUserById(id);
				} catch (NumberFormatException e) {
					continue;
				}
			}
			
			if (member != null || user != null) {
				users.put(id, new JSONObject().put("name", member != null ? member.getEffectiveName() : user.getName()));
			}
		}
		
		JSONObject channels = new JSONObject();
		while (channelMention.find()) {
			String id = channelMention.group(1);
			
			TextChannel channel;
			try {
				channel = Sx4Bot.getShardManager().getTextChannelById(id);
			} catch (NumberFormatException e) {
				continue;
			}
			
			if (channel != null) {
				channels.put(id, new JSONObject().put("name", channel.getName()));
			}
		}
		
		JSONObject roles = new JSONObject();
		while (roleMention.find()) {
			String id = roleMention.group(1);
			
			Role role;
			try {
				role = guild.getRoleById(id);
			} catch (NumberFormatException e) {
				continue;
			}
			
			if (role != null) {
				roles.put(id, new JSONObject().put("name", role.getName()).put("colour", role.getColorRaw()));
			}
		}
		
		JSONArray emotes = new JSONArray();
		while (emoteMention.find()) {
			emotes.put(emoteMention.group(2));
		}

		return new JSONObject()
				.put("users", users)
				.put("roles", roles)
				.put("channels", channels)
				.put("emotes", emotes);
	}
	
}
