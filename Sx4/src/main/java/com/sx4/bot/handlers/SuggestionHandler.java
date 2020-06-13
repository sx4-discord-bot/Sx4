package com.sx4.bot.handlers;

import java.util.List;
import java.util.stream.Collectors;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.sx4.bot.database.Database;
import com.sx4.bot.utility.ExceptionUtility;

import net.dv8tion.jda.api.events.message.MessageBulkDeleteEvent;
import net.dv8tion.jda.api.events.message.MessageDeleteEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class SuggestionHandler extends ListenerAdapter {

	public void handle(long guildId, List<Long> messageIds) {
		Database.get().updateGuildById(guildId, Updates.pull("suggestion.suggestions", Filters.in("id", messageIds))).whenComplete((result, exception) -> ExceptionUtility.sendErrorMessage(exception));
	}
	
	public void onMessageDelete(MessageDeleteEvent event) {
		this.handle(event.getGuild().getIdLong(), List.of(event.getMessageIdLong()));
	}
	
	public void onMessageBulkDelete(MessageBulkDeleteEvent event) {
		this.handle(event.getGuild().getIdLong(), event.getMessageIds().stream().map(Long::valueOf).collect(Collectors.toList()));
	}
	
}
