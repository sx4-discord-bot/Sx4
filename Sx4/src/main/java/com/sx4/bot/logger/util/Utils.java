package com.sx4.bot.logger.util;

import club.minnced.discord.webhook.send.WebhookEmbed;
import club.minnced.discord.webhook.send.WebhookEmbed.EmbedField;
import net.dv8tion.jda.api.entities.ChannelType;
import net.dv8tion.jda.api.entities.GuildChannel;
import net.dv8tion.jda.api.entities.MessageEmbed;

public class Utils {
	
	public static final String MESSAGE_SEPARATOR = "------------------------------";
	
	public static String limitField(String value) {
		if(value.length() > MessageEmbed.VALUE_MAX_LENGTH) {
			value = value.substring(0, MessageEmbed.VALUE_MAX_LENGTH - 3) + "...";
		}
		
		return value;
	}
	
	public static String getChannelTypeReadable(GuildChannel channel) {
		String type;
		if(channel.getType().equals(ChannelType.TEXT) || channel.getType().equals(ChannelType.VOICE) || channel.getType().equals(ChannelType.STORE)) {
			type = channel.getType().toString().toLowerCase() + " channel";
		}else if(channel.getType().equals(ChannelType.CATEGORY)) {
			type = "category";
		}else{
			return null;
		}
		
		return type;
	}
	
	public static StringBuilder getMessageSeperated(CharSequence sequence) {
		StringBuilder builder = new StringBuilder();
		builder.append(MESSAGE_SEPARATOR);
		builder.append(sequence);
		builder.append('\n').append(MESSAGE_SEPARATOR);
		
		return builder;
	}
	
	public static int getLength(WebhookEmbed embed) {
        int length = 0;

        if (embed.getTitle() != null) {
            length += embed.getTitle().getText().length();
        }
        
        if (embed.getDescription() != null) {
            length += embed.getDescription().length();
        }
        
        if (embed.getAuthor() != null) {
            length += embed.getAuthor().getName().length();
		}
        
        if (embed.getFooter() != null) {
            length += embed.getFooter().getText().length();
        }
        
        if (embed.getFields() != null) {
            for (EmbedField f : embed.getFields()) {
                length += f.getName().length() + f.getValue().length();
            }
        }

        return length;
    }
}