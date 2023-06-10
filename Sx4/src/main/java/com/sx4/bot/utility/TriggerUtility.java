package com.sx4.bot.utility;

import com.jockie.bot.core.command.ICommand;
import com.jockie.bot.core.command.exception.parser.ParseException;
import com.jockie.bot.core.command.impl.CommandEvent;
import com.jockie.bot.core.command.impl.CommandListener;
import com.sx4.bot.core.Sx4;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.database.mongo.MongoDatabase;
import com.sx4.bot.entities.trigger.*;
import com.sx4.bot.formatter.FormatterManager;
import com.sx4.bot.formatter.JsonFormatter;
import com.sx4.bot.formatter.StringFormatter;
import com.sx4.bot.formatter.function.FormatterResponse;
import com.sx4.bot.handlers.TriggerHandler;
import com.sx4.bot.http.HttpCallback;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.emoji.CustomEmoji;
import net.dv8tion.jda.api.entities.emoji.EmojiUnion;
import net.dv8tion.jda.api.requests.Route;
import net.dv8tion.jda.internal.requests.RestActionImpl;
import net.dv8tion.jda.internal.utils.EncodingUtil;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import okhttp3.internal.http.HttpMethod;
import org.bson.Document;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class TriggerUtility {

	public static List<CompletableFuture<Void>> executeActions(Document trigger, Sx4 bot, Message message) {
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
				case EXECUTE_COMMAND -> new ExecuteCommandTriggerAction(manager, actionData, bot.getCommandListener(), message);
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
		GuildMessageChannel messageChannel = channelId == null ? channel : channel.getGuild().getChannelById(GuildMessageChannel.class, channelId);

		return messageChannel.sendMessage(MessageUtility.fromWebhookMessage(MessageUtility.fromJson(action.get("response", Document.class), true).build())).setAllowedMentions(EnumSet.allOf(Message.MentionType.class)).submit()
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

	public static CompletableFuture<Void> executeCommand(FormatterManager manager, Document action, CommandListener listener, Message message) {
		String commandName = action.getString("command");
		String arguments = new StringFormatter(action.getString("arguments"), manager).parse();

		ICommand command = listener.getAllCommands().stream()
			.filter(c -> c.getCommandTrigger().equals(commandName))
			.findFirst()
			.orElse(null);

		if (command == null) {
			return CompletableFuture.failedFuture(new IllegalArgumentException("Command no longer exists?"));
		}

		long nano = System.nanoTime();

		CommandEvent event;
		try {
			event = listener.getCommandParser().parse(listener, command, message, "", commandName, " " + arguments, nano);
		} catch (ParseException e) {
			return CompletableFuture.failedFuture(new IllegalStateException("Command failed to parse: " + e.getMessage()));
		}

		listener.queueCommand(command, event, nano, event.getArguments());

		return CompletableFuture.completedFuture(null);
	}
	
	public static Document parseTriggerAction(Sx4CommandEvent event, TriggerActionType type, Document data) {
		Document action = new Document("type", type.getId());

		Object order = data.get("order");
		if (order instanceof Integer && (int) order >= 0) {
			action.append("order", order);
		}

		if (type == TriggerActionType.REQUEST) {
			Object method = data.get("method");
			if (method == null) {
				throw new IllegalArgumentException("Request method must be given in the `method` field");
			}

			if (!(method instanceof String)) {
				throw new IllegalArgumentException("`method` field has to be a string");
			}

			Object url = data.get("url");
			if (url == null) {
				throw new IllegalArgumentException("Url must be given in the `url` field");
			}

			if (!(url instanceof String)) {
				throw new IllegalArgumentException("`url` field has to be a string");
			}

			Object body = data.get("body"), contentType = data.get("contentType");
			if (body != null && !(body instanceof String)) {
				throw new IllegalArgumentException("`body` field has to be a string");
			}

			if (contentType != null && !(contentType instanceof String)) {
				throw new IllegalArgumentException("`contentType` field has to be a string");
			}

			if ((body == null || contentType == null) && HttpMethod.requiresRequestBody((String) method)) {
				throw new IllegalArgumentException("The request method used requires a body and content type");
			} else if (body != null && !HttpMethod.permitsRequestBody((String) method)) {
				throw new IllegalArgumentException("The request method used can not have a body");
			}

			action.append("url", url).append("method", ((String) method).toUpperCase());

			if (body != null) {
				action.append("contentType", contentType);
				action.append("body", body);
			}

			Object headers = data.get("headers");
			if (headers instanceof Document) {
				action.append("headers", headers);
			}

			Object variable = data.get("variable");
			if (variable instanceof String && !((String) variable).isBlank()) {
				action.append("variable", variable);
			}
		} else if (type == TriggerActionType.SEND_MESSAGE) {
			Object response = data.get("response");
			if (!(response instanceof Document)) {
				throw new IllegalArgumentException("`response` field has to be json");
			}

			MessageUtility.removeFields((Document) response);

			Object channelId = data.get("channelId");
			if (channelId instanceof Long) {
				channelId = Long.toString((long) channelId);
			}

			if (channelId != null && !(channelId instanceof String)) {
				throw new IllegalArgumentException("`channelId` field has to be a string");
			}

			action.append("response", response);
			if (channelId != null) {
				action.append("channelId", channelId);
			}
		} else if (type == TriggerActionType.ADD_REACTION) {
			Object emote = data.get("emote");
			if (emote instanceof String) {
				EmojiUnion emoji = SearchUtility.getEmoji(event.getShardManager(), (String) emote);
				if (emoji == null) {
					throw new IllegalArgumentException("I could not find that emote");
				}

				if (emoji instanceof CustomEmoji) {
					action.append("emote", new Document("id", emoji.asCustom().getId()));
				} else {
					action.append("emote", new Document("name", emoji.getName()));
				}
			} else if (emote instanceof Document emoteData) {

				Object name = emoteData.get("name"), emoteId = emoteData.get("id");
				if (name instanceof String) {
					action.append("emote", new Document("name", name));
				} else if (emoteId instanceof String) {
					action.append("emote", new Document("id", emoteId));
				} else if (emoteId instanceof Long) {
					action.append("emote", new Document("id", Long.toString((long) emoteId)));
				} else {
					throw new IllegalArgumentException("You need to give either `name` or `id` in the `emote` json");
				}
			} else {
				throw new IllegalArgumentException("`emote` field either needs to be json or a string");
			}

			Object channelId = data.get("channelId");
			if (channelId instanceof Long) {
				channelId = Long.toString((long) channelId);
			}

			if (channelId != null && !(channelId instanceof String)) {
				throw new IllegalArgumentException("`channelId` field has to be a string");
			}

			if (channelId != null) {
				action.append("channelId", channelId);
			}

			Object messageId = data.get("messageId");
			if (messageId instanceof Long) {
				messageId = Long.toString((long) messageId);
			}

			if (messageId != null && !(messageId instanceof String)) {
				throw new IllegalArgumentException("`messageId` field has to be a string");
			}

			if (messageId != null) {
				action.append("messageId", messageId);
			}
		} else if (type == TriggerActionType.EXECUTE_COMMAND) {
			Object name = data.get("command");
			if (!(name instanceof String)) {
				throw new IllegalArgumentException("`command` field has to be a string");
			}

			ICommand command = event.getCommandListener().getAllCommands().stream()
				.filter(c -> c.getCommandTrigger().equals(name))
				.findFirst()
				.orElse(null);

			if (command == null) {
				throw new IllegalArgumentException("`" + name + "` is not a valid command");
			}

			action.append("command", command.getCommandTrigger());

			Object arguments = data.get("arguments");
			if (!(arguments instanceof String)) {
				throw new IllegalArgumentException("`arguments` field has to be a string");
			}

			action.append("arguments", arguments);
		} else {
			throw new IllegalArgumentException("That action type is not supported yet");
		}

		return action;
	}

}
