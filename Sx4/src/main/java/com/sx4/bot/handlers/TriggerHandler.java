package com.sx4.bot.handlers;

import com.mongodb.client.model.*;
import com.sx4.bot.core.Sx4;
import com.sx4.bot.database.mongo.MongoDatabase;
import com.sx4.bot.formatter.JsonFormatter;
import com.sx4.bot.utility.MessageUtility;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Message.MentionType;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageUpdateEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import org.bson.Document;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;

public class TriggerHandler implements EventListener {

	private final Sx4 bot;

	public TriggerHandler(Sx4 bot) {
		this.bot = bot;
	}

	public void handle(Message message) {
		User author = message.getAuthor();
		if (author.isBot()) {
			return;
		}

		if (!message.getGuild().getSelfMember().hasPermission(message.getTextChannel(), Permission.MESSAGE_WRITE)) {
			return;
		}

		List<WriteModel<Document>> bulkData = new ArrayList<>();
		this.bot.getMongo().getTriggers(Filters.eq("guildId", message.getGuild().getIdLong()), Projections.include("trigger", "response", "case", "enabled")).forEach(trigger -> {
			if (!trigger.get("enabled", true)) {
				return;
			}

			boolean equals = trigger.get("case", false) ? message.getContentRaw().equals(trigger.getString("trigger")) : message.getContentRaw().equalsIgnoreCase(trigger.getString("trigger"));
			if (equals) {
				Document response = new JsonFormatter(trigger.get("response", Document.class))
					.member(message.getMember())
					.user(message.getAuthor())
					.channel(message.getTextChannel())
					.guild(message.getGuild())
					.addVariable("now", OffsetDateTime.now())
					.addVariable("random", new Random())
					.parse();

				try {
					MessageUtility.fromWebhookMessage(message.getChannel(), MessageUtility.fromJson(response).build()).allowedMentions(EnumSet.allOf(MentionType.class)).queue();
				} catch (IllegalArgumentException e) {
					bulkData.add(new UpdateOneModel<>(Filters.eq("_id", trigger.getObjectId("_id")), Updates.set("enabled", false)));
				}
			}
		});

		if (!bulkData.isEmpty()) {
			this.bot.getMongo().bulkWriteTriggers(bulkData).whenComplete(MongoDatabase.exceptionally());
		}
	}

	@Override
	public void onEvent(GenericEvent event) {
		if (event instanceof GuildMessageReceivedEvent) {
			this.handle(((GuildMessageReceivedEvent) event).getMessage());
		} else if (event instanceof GuildMessageUpdateEvent) {
			this.handle(((GuildMessageUpdateEvent) event).getMessage());
		}
	}

}
