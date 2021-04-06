package com.sx4.bot.handlers;

import com.mongodb.client.model.Filters;
import com.sx4.bot.core.Sx4;
import com.sx4.bot.database.Database;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.message.MessageBulkDeleteEvent;
import net.dv8tion.jda.api.events.message.MessageDeleteEvent;
import net.dv8tion.jda.api.hooks.EventListener;

import java.util.List;
import java.util.stream.Collectors;

public class GiveawayHandler implements EventListener {

	private final Sx4 bot;

	public GiveawayHandler(Sx4 bot) {
		this.bot = bot;
	}

	public void handle(List<Long> messageIds) {
		this.bot.getDatabase().deleteManyGiveaways(Filters.in("messageId", messageIds)).whenComplete(Database.exceptionally(this.bot.getShardManager()));
		
		messageIds.forEach(this.bot.getGiveawayManager()::deleteExecutor);
	}

	@Override
	public void onEvent(GenericEvent event) {
		if (event instanceof MessageBulkDeleteEvent) {
			this.handle(((MessageBulkDeleteEvent) event).getMessageIds().stream().map(Long::valueOf).collect(Collectors.toList()));
		} else if (event instanceof MessageDeleteEvent) {
			this.handle(List.of(((MessageDeleteEvent) event).getMessageIdLong()));
		}
	}

}
