package com.sx4.bot.handlers;

import com.mongodb.client.model.Filters;
import com.sx4.bot.core.Sx4;
import com.sx4.bot.database.Database;
import net.dv8tion.jda.api.events.message.MessageBulkDeleteEvent;
import net.dv8tion.jda.api.events.message.MessageDeleteEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.List;
import java.util.stream.Collectors;

public class GiveawayHandler extends ListenerAdapter {

	private final Sx4 bot;

	public GiveawayHandler(Sx4 bot) {
		this.bot = bot;
	}

	public void handle(List<Long> messageIds) {
		this.bot.getDatabase().deleteManyGiveaways(Filters.in("_id", messageIds)).whenComplete(Database.exceptionally(this.bot.getShardManager()));
		
		messageIds.forEach(this.bot.getGiveawayManager()::deleteExecutor);
	}
	
	public void onMessageBulkDelete(MessageBulkDeleteEvent event) {
		this.handle(event.getMessageIds().stream().map(Long::valueOf).collect(Collectors.toList()));
	}
	
	public void onMessageDelete(MessageDeleteEvent event) {
		this.handle(List.of(event.getMessageIdLong()));
	}
	
}
