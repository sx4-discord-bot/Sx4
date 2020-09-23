package com.sx4.bot.cache;

import com.sx4.bot.database.Database;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.MessageBulkDeleteEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageDeleteEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.bson.Document;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class GuildMessageCache extends ListenerAdapter {
	
	private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
	
	private Document getData(Message message) {
		return new Document("_id", message.getIdLong())
			.append("guildId", message.getGuild().getIdLong())
			.append("channelId", message.getChannel().getIdLong())
			.append("authorId", message.getAuthor().getIdLong())
			.append("pinned", message.isPinned())
			.append("content", message.getContentRaw())
			.append("updated", LocalDateTime.now(ZoneOffset.UTC));
	}

	public void onGuildMessageReceived(GuildMessageReceivedEvent event) {
		Database.get().insertMessage(this.getData(event.getMessage())).whenComplete(Database.exceptionally());
	}
	
	public void onGuildMessageUpdate(GuildMessageUpdateEvent event) {
		Database.get().replaceMessage(this.getData(event.getMessage())).whenComplete(Database.exceptionally());
	}

	public void handleDelete(List<Long> messageIds) {
		this.executor.schedule(() -> Database.get().deleteMessages(messageIds).whenComplete(Database.exceptionally()), 5, TimeUnit.SECONDS);
	}
	
	public void onGuildMessageDelete(GuildMessageDeleteEvent event) {
		this.handleDelete(List.of(event.getMessageIdLong()));
	}

	public void onMessageBulkDelete(MessageBulkDeleteEvent event) {
		this.handleDelete(event.getMessageIds().stream().map(Long::parseLong).collect(Collectors.toList()));
	}
}
