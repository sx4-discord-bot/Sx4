package com.sx4.bot.cache;

import java.util.ArrayList;
import java.util.List;

import com.sx4.bot.settings.Settings;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.message.guild.GuildMessageDeleteEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.internal.utils.tuple.Pair;

public class ChangesMessageCache extends ListenerAdapter {
	
	private static final List<Pair<Long, String>> MESSAGES = new ArrayList<>();
	
	public static List<Pair<Long, String>> getMessages() {
		return MESSAGES;
	}

	public void onGuildMessageReceived(GuildMessageReceivedEvent event) {
		Guild guild = event.getJDA().getShardManager().getGuildById(Settings.SUPPORT_SERVER_ID);
		if (guild == null) {
			return;
		}
		
		TextChannel channel = guild.getTextChannelById(Settings.CHANGES_CHANNEL_ID);
		
		if (MESSAGES.isEmpty()) {
			channel.getHistory().retrievePast(100).queue(channelMessages -> {
				if (MESSAGES.isEmpty()) {
					for (Message channelMessage : channelMessages) {
						MESSAGES.add(Pair.of(channelMessage.getIdLong(), channelMessage.getContentRaw()));
					}
				}
			});
		} else {
			if (event.getChannel().equals(channel)) {
				if (MESSAGES.size() == 100) {
					MESSAGES.remove(99);
				}
				
				MESSAGES.add(0, Pair.of(event.getMessage().getIdLong(), event.getMessage().getContentRaw()));
			}
		}
	}
	
	public void onGuildMessageUpdate(GuildMessageUpdateEvent event) {
		Guild guild = event.getJDA().getShardManager().getGuildById(Settings.SUPPORT_SERVER_ID);
		if (guild == null) {
			return;
		}
		
		TextChannel channel = guild.getTextChannelById(Settings.CHANGES_CHANNEL_ID);
		
		if (MESSAGES.isEmpty()) {
			channel.getHistory().retrievePast(100).queue(channelMessages -> {
				if (MESSAGES.isEmpty()) {
					for (Message channelMessage : channelMessages) {
						MESSAGES.add(Pair.of(channelMessage.getIdLong(), channelMessage.getContentRaw()));
					}
				}
			});
		} else {
			if (event.getChannel().equals(channel)) {
				for (Pair<Long, String> message : MESSAGES) {
					if (message.getLeft() == event.getMessageIdLong()) {
						int index = MESSAGES.indexOf(message);
						MESSAGES.remove(message);
						MESSAGES.add(index, Pair.of(event.getMessage().getIdLong(), event.getMessage().getContentRaw()));
						return;
					}
				}
			}
		}
	}
	
	public void onGuildMessageDelete(GuildMessageDeleteEvent event) {
		Guild guild = event.getJDA().getShardManager().getGuildById(Settings.SUPPORT_SERVER_ID);
		if (guild == null) {
			return;
		}
		
		TextChannel channel = guild.getTextChannelById(Settings.CHANGES_CHANNEL_ID);
		
		if (MESSAGES.isEmpty()) {
			channel.getHistory().retrievePast(100).queue(channelMessages -> {
				if (MESSAGES.isEmpty()) {
					for (Message channelMessage : channelMessages) {
						MESSAGES.add(Pair.of(channelMessage.getIdLong(), channelMessage.getContentRaw()));
					}
				}
			});
		} else {
			if (event.getChannel().equals(channel)) {
				for (Pair<Long, String> message : MESSAGES) {
					if (message.getLeft() == event.getMessageIdLong()) {
						MESSAGES.remove(message);
						return;
					}
				}
			}
		}
	}
	
}
