package com.sx4.bot.handlers;

import com.mongodb.client.model.*;
import com.sx4.bot.core.Sx4;
import com.sx4.bot.database.mongo.MongoDatabase;
import com.sx4.bot.utility.ExceptionUtility;
import com.sx4.bot.utility.FutureUtility;
import com.sx4.bot.utility.TriggerUtility;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.GuildMessageChannel;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.MessageUpdateEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import okhttp3.OkHttpClient;
import org.bson.Document;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

public class TriggerHandler implements EventListener {

	public final static OkHttpClient CLIENT = new OkHttpClient.Builder()
		.callTimeout(5, TimeUnit.SECONDS)
		.build();

	private final Sx4 bot;

	public TriggerHandler(Sx4 bot) {
		this.bot = bot;
	}

	public void handle(Message message) {
		if (!message.isFromGuild()) {
			return;
		}

		User author = message.getAuthor();
		if (author.isBot()) {
			return;
		}

		GuildMessageChannel channel = message.getGuildChannel();
		if (!message.getGuild().getSelfMember().hasPermission(channel, Permission.MESSAGE_SEND)) {
			return;
		}

		this.bot.getExecutor().submit(() -> {
			List<WriteModel<Document>> bulkData = new ArrayList<>();
			this.bot.getMongo().getTriggers(Filters.eq("guildId", message.getGuild().getIdLong()), Projections.include("trigger", "response", "case", "enabled", "actions")).forEach(trigger -> {
				if (!trigger.get("enabled", true)) {
					return;
				}

				boolean equals = trigger.get("case", false) ? message.getContentRaw().equals(trigger.getString("trigger")) : message.getContentRaw().equalsIgnoreCase(trigger.getString("trigger"));
				if (!equals) {
					return;
				}

				List<CompletableFuture<Void>> futures = TriggerUtility.executeActions(trigger, message);

				FutureUtility.allOf(futures).whenComplete(($, exception) -> {
					Throwable cause = exception instanceof CompletionException ? exception.getCause() : exception;
					if (cause instanceof IllegalArgumentException) {
						bulkData.add(new UpdateOneModel<>(Filters.eq("_id", trigger.getObjectId("_id")), Updates.set("enabled", false)));
					}

					ExceptionUtility.sendExceptionally(channel, exception);
				});
			});

			if (!bulkData.isEmpty()) {
				this.bot.getMongo().bulkWriteTriggers(bulkData).whenComplete(MongoDatabase.exceptionally());
			}
		});
	}

	@Override
	public void onEvent(@NotNull GenericEvent event) {
		if (event instanceof MessageReceivedEvent) {
			this.handle(((MessageReceivedEvent) event).getMessage());
		} else if (event instanceof MessageUpdateEvent) {
			this.handle(((MessageUpdateEvent) event).getMessage());
		}
	}

}
