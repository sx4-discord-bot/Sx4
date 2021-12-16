package com.sx4.bot.handlers;

import com.mongodb.client.model.*;
import com.sx4.bot.core.Sx4;
import com.sx4.bot.database.mongo.MongoDatabase;
import com.sx4.bot.database.mongo.model.Operators;
import com.sx4.bot.entities.management.TriggerActionType;
import com.sx4.bot.formatter.Formatter;
import com.sx4.bot.formatter.FormatterManager;
import com.sx4.bot.formatter.JsonFormatter;
import com.sx4.bot.formatter.function.FormatterResponseBody;
import com.sx4.bot.http.HttpCallback;
import com.sx4.bot.utility.ExceptionUtility;
import com.sx4.bot.utility.FutureUtility;
import com.sx4.bot.utility.MessageUtility;
import com.sx4.bot.utility.RequestUtility;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Message.MentionType;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageUpdateEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import okhttp3.*;
import org.bson.Document;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class TriggerHandler implements EventListener {

	private final OkHttpClient client = new OkHttpClient.Builder()
		.callTimeout(3, TimeUnit.SECONDS)
		.build();

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
		this.bot.getMongo().getTriggers(Filters.eq("guildId", message.getGuild().getIdLong()), Projections.include("trigger", "response", "case", "enabled", "actions")).forEach(trigger -> {
			if (!trigger.get("enabled", true)) {
				return;
			}

			boolean equals = trigger.get("case", false) ? message.getContentRaw().equals(trigger.getString("trigger")) : message.getContentRaw().equalsIgnoreCase(trigger.getString("trigger"));
			if (!equals) {
				return;
			}

			FormatterManager manager = FormatterManager.getDefaultManager()
				.addVariable("member", message.getMember())
				.addVariable("user", author)
				.addVariable("channel", message.getTextChannel())
				.addVariable("server", message.getGuild())
				.addVariable("now", OffsetDateTime.now())
				.addVariable("random", new Random());

			List<Document> actions = trigger.getList("actions", Document.class, Collections.emptyList());

			List<CompletableFuture<Void>> futures = new ArrayList<>();
			for (Document action : actions) {
				TriggerActionType type = TriggerActionType.fromId(action.getInteger("type"));
				if (type == TriggerActionType.REQUEST) {
					String body = action.getString("body");

					Request.Builder request;
					try {
						request = new Request.Builder()
							.url(RequestUtility.getWorkerUrl(new Formatter(action.getString("url"), manager).parse()))
							.method(action.getString("method"), body == null ? null : RequestBody.create(MediaType.parse(action.getString("contentType")), new Formatter(body, manager).parse()));
					} catch (IllegalArgumentException e) {
						bulkData.add(new UpdateOneModel<>(Filters.eq("_id", trigger.getObjectId("_id")), List.of(Operators.set("actions", Operators.filter("$actions", Operators.ne("$$this.id", type.getId()))))));
						continue;
					}

					Document headers = action.get("headers", MongoDatabase.EMPTY_DOCUMENT);
					for (String header : headers.keySet()) {
						request.addHeader(new Formatter(header, manager).parse(), new Formatter(headers.getString(header), manager).parse());
					}

					CompletableFuture<Void> future;
					if (action.get("wait", true)) {
						future = new CompletableFuture<>();
						futures.add(future);
					} else {
						future = null;
					}

					this.client.newCall(request.build()).enqueue((HttpCallback) response -> {
						if (future == null) {
							return;
						}

						ResponseBody responseBody = response.body();
						if (responseBody.contentLength() <= 100_000_000) {
							manager.addVariable(action.get("variable", "body"), new FormatterResponseBody(responseBody.string()));
						}

						future.complete(null);
					});
				}
			}

			FutureUtility.allOf(futures).whenComplete(($, exception) -> {
				if (ExceptionUtility.sendErrorMessage(exception)) {
					return;
				}

				Document response = new JsonFormatter(trigger.get("response", Document.class), manager).parse();

				try {
					MessageUtility.fromWebhookMessage(message.getChannel(), MessageUtility.fromJson(response).build()).allowedMentions(EnumSet.allOf(MentionType.class)).queue();
				} catch (IllegalArgumentException e) {
					bulkData.add(new UpdateOneModel<>(Filters.eq("_id", trigger.getObjectId("_id")), Updates.set("enabled", false)));
				}
			});
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
