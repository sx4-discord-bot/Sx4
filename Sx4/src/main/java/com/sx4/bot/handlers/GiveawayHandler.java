package com.sx4.bot.handlers;

import java.util.List;
import java.util.stream.Collectors;

import com.mongodb.client.model.Filters;
import com.sx4.bot.database.Database;
import com.sx4.bot.managers.GiveawayManager;
import com.sx4.bot.utility.ExceptionUtility;

import net.dv8tion.jda.api.events.message.MessageBulkDeleteEvent;
import net.dv8tion.jda.api.events.message.MessageDeleteEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class GiveawayHandler extends ListenerAdapter {
	
	private final GiveawayManager manager = GiveawayManager.get();

	public void handle(List<Long> messageIds) {
		Database.get().deleteManyGiveaways(Filters.in("_id", messageIds)).whenComplete((reuslt, exception) -> ExceptionUtility.sendErrorMessage(exception));
		
		messageIds.forEach(this.manager::cancelExecutor);
	}
	
	public void onMessageBulkDelete(MessageBulkDeleteEvent event) {
		this.handle(event.getMessageIds().stream().map(Long::valueOf).collect(Collectors.toList()));
	}
	
	public void onMessageDelete(MessageDeleteEvent event) {
		this.handle(List.of(event.getMessageIdLong()));
	}
	
}
