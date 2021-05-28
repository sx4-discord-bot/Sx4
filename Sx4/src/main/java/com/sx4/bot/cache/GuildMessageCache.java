package com.sx4.bot.cache;

import com.sx4.bot.core.Sx4;
import com.sx4.bot.database.mongo.MongoDatabase;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.message.MessageBulkDeleteEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageDeleteEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageUpdateEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import org.bson.Document;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class GuildMessageCache implements EventListener {
	
	private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

	private final Sx4 bot;

	public GuildMessageCache(Sx4 bot) {
		this.bot = bot;
	}
	
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
		if (event.getAuthor().isBot()) {
			return;
		}

		this.bot.getMongo().insertMessage(this.getData(event.getMessage())).whenComplete(MongoDatabase.exceptionally(this.bot.getShardManager()));
	}
	
	public void onGuildMessageUpdate(GuildMessageUpdateEvent event) {
		if (event.getAuthor().isBot()) {
			return;
		}

		this.executor.schedule(() -> this.bot.getMongo().replaceMessage(this.getData(event.getMessage())).whenComplete(MongoDatabase.exceptionally(this.bot.getShardManager())), 50, TimeUnit.MILLISECONDS);
	}

	public void handle(List<Long> messageIds) {
		this.executor.schedule(() -> this.bot.getMongo().deleteMessages(messageIds).whenComplete(MongoDatabase.exceptionally(this.bot.getShardManager())), 5, TimeUnit.MINUTES);
	}

	@Override
	public void onEvent(GenericEvent event) {
		if (event instanceof GuildMessageDeleteEvent) {
			this.handle(List.of(((GuildMessageDeleteEvent) event).getMessageIdLong()));
		} else if (event instanceof MessageBulkDeleteEvent) {
			this.handle(((MessageBulkDeleteEvent) event).getMessageIds().stream().map(Long::parseLong).collect(Collectors.toList()));
		} else if (event instanceof GuildMessageReceivedEvent) {
			this.onGuildMessageReceived((GuildMessageReceivedEvent) event);
		} else if (event instanceof GuildMessageUpdateEvent) {
			this.onGuildMessageUpdate((GuildMessageUpdateEvent) event);
		}
	}

}
