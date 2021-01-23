package com.sx4.bot.handlers;

import com.mongodb.client.model.*;
import com.sx4.bot.database.Database;
import com.sx4.bot.formatter.JsonFormatter;
import com.sx4.bot.formatter.parser.FormatterRandomParser;
import com.sx4.bot.utility.MessageUtility;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Message.MentionType;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.bson.Document;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public class TriggerHandler extends ListenerAdapter {

	private final Database database = Database.get();

	public void handle(Message message) {
		User author = message.getAuthor();
		if (author.isBot()) {
			return;
		}

		List<WriteModel<Document>> bulkData = new ArrayList<>();
		this.database.getTriggers(Filters.eq("guildId", message.getGuild().getIdLong()), Projections.include("trigger", "response", "case", "enabled")).forEach(trigger -> {
			if (!trigger.get("enabled", true)) {
				return;
			}

			boolean equals = trigger.get("case", false) ? message.getContentRaw().equals(trigger.getString("trigger")) : message.getContentRaw().equalsIgnoreCase(trigger.getString("trigger"));
			if (equals) {
				Document response = new JsonFormatter(trigger.get("response", Document.class))
					.member(message.getMember())
					.channel(message.getTextChannel())
					.guild(message.getGuild())
					.appendFunction("random", new FormatterRandomParser())
					.parse();

				try {
					MessageUtility.fromWebhookMessage(message.getChannel(), MessageUtility.fromJson(response).build()).allowedMentions(EnumSet.allOf(MentionType.class)).queue();
				} catch (IllegalArgumentException e) {
					bulkData.add(new UpdateOneModel<>(Filters.eq("_id", trigger.getObjectId("_id")), Updates.set("enabled", false)));
				}
			}
		});

		if (!bulkData.isEmpty()) {
			this.database.bulkWriteTriggers(bulkData).whenComplete(Database.exceptionally());
		}
	}

	public void onGuildMessageReceived(GuildMessageReceivedEvent event) {
		this.handle(event.getMessage());
	}

	public void onGuildMessageUpdate(GuildMessageUpdateEvent event) {
		this.handle(event.getMessage());
	}

}
