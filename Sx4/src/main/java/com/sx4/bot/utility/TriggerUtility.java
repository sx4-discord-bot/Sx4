package com.sx4.bot.utility;

import com.sx4.bot.database.mongo.MongoDatabase;
import com.sx4.bot.entities.management.TriggerActionType;
import com.sx4.bot.formatter.Formatter;
import com.sx4.bot.formatter.FormatterManager;
import com.sx4.bot.formatter.JsonFormatter;
import com.sx4.bot.formatter.function.FormatterResponseBody;
import com.sx4.bot.handlers.TriggerHandler;
import com.sx4.bot.http.HttpCallback;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.internal.requests.RestActionImpl;
import net.dv8tion.jda.internal.requests.Route;
import net.dv8tion.jda.internal.utils.EncodingUtil;
import okhttp3.*;
import org.bson.Document;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class TriggerUtility {

	public static List<CompletableFuture<Void>> executeActions(Document trigger, Message message) {
		TextChannel channel = message.getTextChannel();

		FormatterManager manager = FormatterManager.getDefaultManager()
			.addVariable("member", message.getMember())
			.addVariable("user", message.getAuthor())
			.addVariable("channel", channel)
			.addVariable("server", message.getGuild())
			.addVariable("now", OffsetDateTime.now())
			.addVariable("random", new Random());

		List<Document> actions = trigger.getList("actions", Document.class, Collections.emptyList()).stream()
			.sorted(Comparator.comparingInt(d -> d.getInteger("order", -1)))
			.collect(Collectors.toList());

		CompletableFuture<Void> orderedFuture = CompletableFuture.completedFuture(null);
		List<CompletableFuture<Void>> futures = new ArrayList<>();
		for (Document action : actions) {
			TriggerActionType type = TriggerActionType.fromId(action.getInteger("type"));
			if (type == null) {
				continue;
			}

			boolean ordered = action.getInteger("order", -1) != -1;

			CompletableFuture<Void> future = null;
			if (type == TriggerActionType.REQUEST) {
				if (ordered) {
					orderedFuture = orderedFuture.thenCompose($ -> TriggerUtility.executeRequest(manager, action));
				} else {
					future = TriggerUtility.executeRequest(manager, action);
				}
			} else if (type == TriggerActionType.SEND_MESSAGE) {
				if (ordered) {
					orderedFuture = orderedFuture.thenCompose($ -> TriggerUtility.sendMessage(manager, action, channel));
				} else {
					future = TriggerUtility.sendMessage(manager, action, channel);
				}
			} else if (type == TriggerActionType.ADD_REACTION) {
				if (ordered) {
					orderedFuture = orderedFuture.thenCompose($ -> TriggerUtility.addReaction(manager, action, message));
				} else {
					future = TriggerUtility.addReaction(manager, action, message);
				}
			}

			if (future != null) {
				futures.add(future);
			}
		}

		futures.add(orderedFuture);

		return futures;
	}

	public static CompletableFuture<Void> executeRequest(FormatterManager manager, Document oldAction) {
		Document action = new JsonFormatter(oldAction, manager).parse();

		String body = action.getString("body");

		Request.Builder request;
		try {
			request = new Request.Builder()
				.url(RequestUtility.getWorkerUrl(action.getString("url")))
				.method(action.getString("method"), body == null ? null : RequestBody.create(MediaType.parse(action.getString("contentType")), new Formatter(body, manager).parse()));
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
			return CompletableFuture.failedFuture(e);
		}

		Document headers = action.get("headers", MongoDatabase.EMPTY_DOCUMENT);
		for (String header : headers.keySet()) {
			request.addHeader(header, headers.getString(header));
		}

		CompletableFuture<Void> future = new CompletableFuture<>();
		TriggerHandler.CLIENT.newCall(request.build()).enqueue((HttpCallback) response -> {
			ResponseBody responseBody = response.body();
			if (responseBody != null && responseBody.contentLength() <= 100_000_000) {
				manager.addVariable(action.get("variable", "body"), new FormatterResponseBody(responseBody.string()));
			}

			future.complete(null);
		});

		return future;
	}

	public static CompletableFuture<Void> sendMessage(FormatterManager manager, Document oldAction, TextChannel channel) {
		Document action = new JsonFormatter(oldAction, manager).parse();

		String channelId = action.getString("channelId");
		TextChannel textChannel = channelId == null ? channel : channel.getGuild().getTextChannelById(channelId);

		return MessageUtility.fromWebhookMessage(textChannel, MessageUtility.fromJson(action.get("response", Document.class)).build()).allowedMentions(EnumSet.allOf(Message.MentionType.class)).submit()
			.thenApply(message -> {
				manager.addVariable(action.get("variable", "message"), message);
				return null;
			});
	}

	public static CompletableFuture<Void> addReaction(FormatterManager manager, Document oldAction, Message message) {
		Document action = new JsonFormatter(oldAction, manager).parse();

		String channelId = action.get("channelId", message.getTextChannel().getId());
		String messageId = action.get("messageId", message.getId());

		Document emote = action.get("emote", Document.class);
		String reactionCode = emote.containsKey("name") ? emote.getString("name") : "a:" + emote.getString("id");

		Route.CompiledRoute route = Route.Messages.ADD_REACTION.compile(channelId, messageId, EncodingUtil.encodeReaction(reactionCode), "@me");
		return new RestActionImpl<>(message.getJDA(), route).submit().thenApply($ -> null);
	}

}
