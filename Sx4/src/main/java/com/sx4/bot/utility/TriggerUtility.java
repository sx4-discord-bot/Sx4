package com.sx4.bot.utility;

import com.sx4.bot.database.mongo.MongoDatabase;
import com.sx4.bot.entities.trigger.*;
import com.sx4.bot.formatter.FormatterManager;
import com.sx4.bot.formatter.JsonFormatter;
import com.sx4.bot.formatter.function.FormatterResponse;
import com.sx4.bot.handlers.TriggerHandler;
import com.sx4.bot.http.HttpCallback;
import net.dv8tion.jda.api.entities.BaseGuildMessageChannel;
import net.dv8tion.jda.api.entities.GuildMessageChannel;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.internal.requests.RestActionImpl;
import net.dv8tion.jda.internal.requests.Route;
import net.dv8tion.jda.internal.utils.EncodingUtil;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import org.bson.Document;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class TriggerUtility {

	public static List<CompletableFuture<Void>> executeActions(Document trigger, Message message) {
		GuildMessageChannel channel = message.getGuildChannel();

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
		for (Document actionData : actions) {
			TriggerActionType type = TriggerActionType.fromId(actionData.getInteger("type"));
			if (type == null) {
				continue;
			}

			TriggerAction action = switch (type) {
				case REQUEST -> new RequestTriggerAction(manager, actionData);
				case SEND_MESSAGE -> new SendMessageTriggerAction(manager, actionData, channel);
				case ADD_REACTION -> new AddReactionTriggerAction(manager, actionData, message);
			};

			if (actionData.containsKey("order")) {
				orderedFuture = orderedFuture.thenCompose($ -> action.execute());
			} else {
				futures.add(action.execute());
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
				.method(action.getString("method"), body == null ? null : RequestBody.create(MediaType.parse(action.getString("contentType")), body));
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
				manager.addVariable(action.get("variable", "response"), new FormatterResponse(response));
			}

			future.complete(null);
		});

		return future;
	}

	public static CompletableFuture<Void> sendMessage(FormatterManager manager, Document oldAction, GuildMessageChannel channel) {
		Document action = new JsonFormatter(oldAction, manager).parse();

		String channelId = action.getString("channelId");
		GuildMessageChannel messageChannel = channelId == null ? channel : channel.getGuild().getChannelById(BaseGuildMessageChannel.class, channelId);

		return MessageUtility.fromWebhookMessage(messageChannel, MessageUtility.fromJson(action.get("response", Document.class)).build()).allowedMentions(EnumSet.allOf(Message.MentionType.class)).submit()
			.thenApply(message -> {
				manager.addVariable(action.get("variable", "message"), message);
				return null;
			});
	}

	public static CompletableFuture<Void> addReaction(FormatterManager manager, Document oldAction, Message message) {
		Document action = new JsonFormatter(oldAction, manager).parse();

		String channelId = action.get("channelId", message.getChannel().getId());
		String messageId = action.get("messageId", message.getId());

		Document emote = action.get("emote", Document.class);
		String reactionCode = emote.containsKey("name") ? emote.getString("name") : "a:" + emote.getString("id");

		Route.CompiledRoute route = Route.Messages.ADD_REACTION.compile(channelId, messageId, EncodingUtil.encodeReaction(reactionCode), "@me");
		return new RestActionImpl<>(message.getJDA(), route).submit().thenApply($ -> null);
	}

}
