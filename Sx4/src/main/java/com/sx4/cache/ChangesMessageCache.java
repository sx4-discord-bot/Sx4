package com.sx4.cache;

import java.util.ArrayList;
import java.util.List;

import com.sx4.settings.Settings;

import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.events.message.guild.GuildMessageDeleteEvent;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.core.events.message.guild.GuildMessageUpdateEvent;
import net.dv8tion.jda.core.hooks.ListenerAdapter;
import net.dv8tion.jda.core.utils.tuple.Pair;

public class ChangesMessageCache extends ListenerAdapter {
	
	private static List<Pair<String, String>> messages = new ArrayList<>();
	
	public static List<Pair<String, String>> getMessages() {
		return messages;
	}

	public void onGuildMessageReceived(GuildMessageReceivedEvent event) {
		Guild guild = event.getJDA().asBot().getShardManager().getGuildById(Settings.SUPPORT_SERVER_ID);
		if (guild == null) {
			return;
		}
		
		TextChannel channel = guild.getTextChannelById(Settings.CHANGES_CHANNEL_ID);
		
		if (messages.isEmpty()) {
			channel.getHistory().retrievePast(100).queue(channelMessages -> {
				if (messages.isEmpty()) {
					for (Message channelMessage : channelMessages) {
						messages.add(Pair.of(channelMessage.getId(), channelMessage.getContentRaw()));
					}
				}
			});
		} else {
			if (event.getChannel().equals(channel)) {
				if (messages.size() == 100) {
					messages.remove(99);
				}
				
				messages.add(0, Pair.of(event.getMessage().getId(), event.getMessage().getContentRaw()));
			}
		}
	}
	
	public void onGuildMessageUpdate(GuildMessageUpdateEvent event) {
		Guild guild = event.getJDA().asBot().getShardManager().getGuildById(Settings.SUPPORT_SERVER_ID);
		if (guild == null) {
			return;
		}
		
		TextChannel channel = guild.getTextChannelById(Settings.CHANGES_CHANNEL_ID);
		
		if (messages.isEmpty()) {
			channel.getHistory().retrievePast(100).queue(channelMessages -> {
				if (messages.isEmpty()) {
					for (Message channelMessage : channelMessages) {
						messages.add(Pair.of(channelMessage.getId(), channelMessage.getContentRaw()));
					}
				}
			});
		} else {
			if (event.getChannel().equals(channel)) {
				for (Pair<String, String> message : messages) {
					if (message.getLeft().equals(event.getMessageId())) {
						int index = messages.indexOf(message);
						messages.remove(message);
						messages.add(index, Pair.of(event.getMessage().getId(), event.getMessage().getContentRaw()));
						return;
					}
				}
			}
		}
	}
	
	public void onGuildMessageDelete(GuildMessageDeleteEvent event) {
		Guild guild = event.getJDA().asBot().getShardManager().getGuildById(Settings.SUPPORT_SERVER_ID);
		if (guild == null) {
			return;
		}
		
		TextChannel channel = guild.getTextChannelById(Settings.CHANGES_CHANNEL_ID);
		
		if (messages.isEmpty()) {
			channel.getHistory().retrievePast(100).queue(channelMessages -> {
				if (messages.isEmpty()) {
					for (Message channelMessage : channelMessages) {
						messages.add(Pair.of(channelMessage.getId(), channelMessage.getContentRaw()));
					}
				}
			});
		} else {
			if (event.getChannel().equals(channel)) {
				for (Pair<String, String> message : messages) {
					if (message.getLeft().equals(event.getMessageId())) {
						messages.remove(message);
						return;
					}
				}
			}
		}
	}
	
}
