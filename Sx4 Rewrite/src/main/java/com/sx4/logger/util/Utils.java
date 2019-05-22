package com.sx4.logger.util;

import net.dv8tion.jda.core.entities.Channel;
import net.dv8tion.jda.core.entities.ChannelType;
import net.dv8tion.jda.core.entities.MessageEmbed;

public class Utils {
	
	public static final String MESSAGE_SEPARATOR = "------------------------------";
	
	public static String limitField(String value) {
		if(value.length() > MessageEmbed.VALUE_MAX_LENGTH) {
			value = value.substring(0, MessageEmbed.VALUE_MAX_LENGTH - 3) + "...";
		}
		
		return value;
	}
	
	public static String getChannelTypeReadable(Channel channel) {
		String type;
		if(channel.getType().equals(ChannelType.TEXT) || channel.getType().equals(ChannelType.VOICE)) {
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
}