package com.sx4.bot.cache.message;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.bson.Document;

import com.sx4.bot.database.Database;
import com.sx4.bot.utility.ExceptionUtility;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.guild.GuildMessageDeleteEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class GuildMessageCache extends ListenerAdapter {
	
	private static final GuildMessageCache INSTANCE = new GuildMessageCache();
	
	public static GuildMessageCache get() {
		return GuildMessageCache.INSTANCE;
	}
	
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
		Database.get().insertMessage(this.getData(event.getMessage())).whenComplete((result, exception) -> ExceptionUtility.sendErrorMessage(exception));
	}
	
	public void onGuildMessageUpdate(GuildMessageUpdateEvent event) {
		Database.get().replaceMessage(this.getData(event.getMessage())).whenComplete((result, exception) -> ExceptionUtility.sendErrorMessage(exception));
	}
	
	public void onGuildMessageDelete(GuildMessageDeleteEvent event) {
		this.executor.schedule(() -> Database.get().deleteMessageById(event.getMessageIdLong()).whenComplete((result, exception) -> ExceptionUtility.sendErrorMessage(exception)), 5, TimeUnit.SECONDS);
	}
	
}
