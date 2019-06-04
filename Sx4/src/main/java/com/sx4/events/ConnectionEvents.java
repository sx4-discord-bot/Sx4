package com.sx4.events;

import java.time.Instant;

import com.sx4.core.Sx4Bot;
import com.sx4.settings.Settings;

import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.events.DisconnectEvent;
import net.dv8tion.jda.core.events.ReconnectedEvent;
import net.dv8tion.jda.core.events.ResumedEvent;
import net.dv8tion.jda.core.hooks.ListenerAdapter;

public class ConnectionEvents extends ListenerAdapter {
	
	public void onDisconnect(DisconnectEvent event) {
		TextChannel eventsChannel = Sx4Bot.getShardManager().getGuildById(Settings.SUPPORT_SERVER_ID).getTextChannelById(Settings.EVENTS_CHANNEL_ID);
		
		EmbedBuilder embed = new EmbedBuilder();
		embed.setAuthor(event.getJDA().getSelfUser().getAsTag(), null, event.getJDA().getSelfUser().getEffectiveAvatarUrl());
		embed.setTimestamp(event.getDisconnectTime());
		embed.setColor(Settings.COLOR_RED);
		embed.addField("Shard", (event.getJDA().getShardInfo().getShardId() + 1) + "/" + event.getJDA().getShardInfo().getShardTotal(), false);
		if (event.getCloseCode() != null) {
			embed.addField("Reason", event.getCloseCode().getMeaning() + " [" + event.getCloseCode().getCode() + "]", false);
		}
		embed.setFooter("Disconnect", null);
		eventsChannel.sendMessage(embed.build()).queue();
	}
	
	public void onResume(ResumedEvent event) {
		TextChannel eventsChannel = Sx4Bot.getShardManager().getGuildById(Settings.SUPPORT_SERVER_ID).getTextChannelById(Settings.EVENTS_CHANNEL_ID);
		
		EmbedBuilder embed = new EmbedBuilder();
		embed.setAuthor(event.getJDA().getSelfUser().getAsTag(), null, event.getJDA().getSelfUser().getEffectiveAvatarUrl());
		embed.setTimestamp(Instant.now());
		embed.setColor(Settings.COLOR_GREEN);
		embed.addField("Shard", (event.getJDA().getShardInfo().getShardId() + 1) + "/" + event.getJDA().getShardInfo().getShardTotal(), true);
		embed.setFooter("Resume", null);
		eventsChannel.sendMessage(embed.build()).queue();
	}
	
	public void onReconnect(ReconnectedEvent event) {
		TextChannel eventsChannel = Sx4Bot.getShardManager().getGuildById(Settings.SUPPORT_SERVER_ID).getTextChannelById(Settings.EVENTS_CHANNEL_ID);
		
		EmbedBuilder embed = new EmbedBuilder();
		embed.setAuthor(event.getJDA().getSelfUser().getAsTag(), null, event.getJDA().getSelfUser().getEffectiveAvatarUrl());
		embed.setTimestamp(Instant.now());
		embed.setColor(Settings.COLOR_GREEN);
		embed.addField("Shard", (event.getJDA().getShardInfo().getShardId() + 1) + "/" + event.getJDA().getShardInfo().getShardTotal(), true);
		embed.setFooter("Reconnect", null);
		eventsChannel.sendMessage(embed.build()).queue();
	}
	
}
