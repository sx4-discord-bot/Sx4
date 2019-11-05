package com.sx4.bot.utils;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import org.bson.Document;

import com.sx4.bot.starboard.StarboardConfiguration;
import com.sx4.bot.starboard.StarboardMessage;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Message.Attachment;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;

public class StarboardUtils {

	public static final List<Document> DEFAULT_STARBOARD_CONFIGURATION = new ArrayList<>();
	
	static {
			DEFAULT_STARBOARD_CONFIGURATION.add(new Document("id", 3).append("message", "⭐ **{stars}** {channel.mention}"));
			DEFAULT_STARBOARD_CONFIGURATION.add(new Document("id", 10).append("message", "🌟 **{stars}** {channel.mention}"));
			DEFAULT_STARBOARD_CONFIGURATION.add(new Document("id", 25).append("message", "🌠 **{stars}** {channel.mention}"));
			DEFAULT_STARBOARD_CONFIGURATION.add(new Document("id", 50).append("message", "✨ **{stars}** {channel.mention}"));
			DEFAULT_STARBOARD_CONFIGURATION.add(new Document("id", 100).append("message", "🎆 **{stars}** {channel.mention}"));
	}
	
	public static final Color STARBOARD_COLOUR = new Color(255, 172, 51);
	
	public static String formatMessage(User user, TextChannel channel, long messageId, List<StarboardConfiguration> configuration, int stars, String message) {
		int index = -1;
		while ((index = message.indexOf('{', index + 1)) != -1) {
		    if (index > 0 && message.charAt(index - 1) == '\\') {
		        message = message.substring(0, index - 1) + message.substring(index);
		        continue;
		    }

		    int endIndex = message.indexOf('}', index + 1);
		    if (endIndex != -1)  {
		        if (message.charAt(endIndex - 1) == '\\') {
		            message = message.substring(0, endIndex - 1) + message.substring(endIndex);
		            continue;
		        } else {
		            String formatter = message.substring(index + 1, endIndex);
		            String placeHolder = message.substring(0, index) + "%s" + message.substring(endIndex + 1);
		            
		            int nextMilestone = StarboardUtils.getNextStarMilestone(configuration, stars);
		            int untilNextMilestone = nextMilestone == 0 ? 0 : nextMilestone - stars;
		            
		            switch (formatter.trim().toLowerCase()) {
		            	case "stars":
		            		message = String.format(placeHolder, String.valueOf(stars));
		            		break;
		            	case "stars.string":
		            		message = String.format(placeHolder, "star" + (stars == 1 ? "" : "s"));
		            		break;
		            	case "stars.suffix":
		            		message = String.format(placeHolder, GeneralUtils.getNumberSuffix(stars));
		            		break;
		            	case "stars.next":
		            		message = String.format(placeHolder, String.valueOf(nextMilestone));
		            		break;
		            	case "stars.next.string":
		            		message = String.format(placeHolder, "star" + (nextMilestone == 1 ? "" : "s"));
		            		break;
		            	case "stars.next.suffix":
		            		message = String.format(placeHolder, GeneralUtils.getNumberSuffix(nextMilestone));
		            		break;
		            	case "stars.next.until":
		            		message = String.format(placeHolder, String.valueOf(untilNextMilestone));
		            		break;
		            	case "stars.next.until.string":
		            		message = String.format(placeHolder, "star" + (untilNextMilestone == 1 ? "" : "s"));
		            		break;
		            	case "stars.next.until.suffix":
		            		message = String.format(placeHolder, GeneralUtils.getNumberSuffix(untilNextMilestone));
		            		break;
		            	case "channel.name":
		            		message = String.format(placeHolder, channel.getName());
		            		break;
		            	case "channel.mention":
		            		message = String.format(placeHolder, channel.getAsMention());
		            		break;
		            	case "message.id": 
		            		message = String.format(placeHolder, String.valueOf(messageId));
		            		break;
		            	case "user.mention":
		            		message = String.format(placeHolder, user.getAsMention());
		            		break;
		            	case "user.name":
		            		message = String.format(placeHolder, user.getName());
		            		break;
		            	case "user": 
		            		message = String.format(placeHolder, user.getAsTag());
		            		break;
		            }
		        }
		    }
		}
		
		return message;
	}
	
	public static int getNextStarMilestone(List<StarboardConfiguration> configuration, int stars) {
		int nextMilestone = 0;
		for (StarboardConfiguration star : configuration) {
			int id = star.getId();
			if (id > stars && (nextMilestone == 0 || id < nextMilestone)) {
				nextMilestone = id;
			}
		}
		
		return nextMilestone;
	}
	
	public static String getCurrentMessage(User user, TextChannel channel, long messageId, List<StarboardConfiguration> configuration, int stars) {
		int currentDisplayIndex = 0;
		for (StarboardConfiguration star : configuration) {
			int id = star.getId();
			if (id <= stars && currentDisplayIndex < id) {
				currentDisplayIndex = id;
			}
		}
		
		return StarboardUtils.formatMessage(user, channel, messageId, configuration, stars, configuration.get(currentDisplayIndex - 1).getMessage());
	}
	
	public static Message getStarboard(User author, long messageId, String content, String image, int stars, TextChannel channel, User user, List<StarboardConfiguration> configuration, String message) {
		EmbedBuilder embed = new EmbedBuilder();
		embed.setAuthor(author == null ? "Unknown User" : author.getAsTag(), null, author == null ? null : author.getEffectiveAvatarUrl());
		embed.setColor(StarboardUtils.STARBOARD_COLOUR);
		embed.addField("Message Link", String.format("[Jump!](https://discordapp.com/channels/%s/%s/%d)", channel.getGuild().getId(), channel.getId(), messageId), false);
		embed.setImage(image);
		
		if (!content.isEmpty()) {
			embed.addField("Message", content, false);
		}
		
		MessageBuilder starboardMessage = new MessageBuilder();
		starboardMessage.setEmbed(embed.build());
		starboardMessage.setContent(StarboardUtils.formatMessage(user, channel, messageId, configuration, stars, message));
		
		return starboardMessage.build();
	}
	
	public static Message getStarboard(Message message, User user, int stars, List<StarboardConfiguration> configuration, String starMessage) {
		String image = message.getAttachments().stream()
				.filter(Attachment::isImage)
				.map(Attachment::getUrl)
				.findFirst()
				.orElse(null);
		
		return StarboardUtils.getStarboard(message.getAuthor(), message.getIdLong(), message.getContentRaw(), image, stars, message.getTextChannel(), user, configuration, starMessage);
	}
	
	public static Message getStarboard(StarboardMessage starboard, TextChannel channel, User user, List<StarboardConfiguration> configuration, String message) {
		return StarboardUtils.getStarboard(starboard.getAuthor(), starboard.getMessageId(), starboard.getContent(), starboard.getImage(), starboard.getStars().size(), channel, user, configuration, message);
	}
	
}
