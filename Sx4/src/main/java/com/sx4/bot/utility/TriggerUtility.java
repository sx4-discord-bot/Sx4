package com.sx4.bot.utility;

import com.jockie.bot.core.command.ICommand;
import com.jockie.bot.core.command.exception.parser.ParseException;
import com.jockie.bot.core.command.impl.CommandEvent;
import com.jockie.bot.core.command.impl.CommandListener;
import com.sx4.bot.core.Sx4;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.core.Sx4CommandListener;
import com.sx4.bot.database.mongo.MongoDatabase;
import com.sx4.bot.entities.trigger.*;
import com.sx4.bot.formatter.output.Formatter;
import com.sx4.bot.formatter.output.FormatterManager;
import com.sx4.bot.formatter.output.JsonFormatter;
import com.sx4.bot.formatter.output.StringFormatter;
import com.sx4.bot.formatter.output.function.FormatterResponse;
import com.sx4.bot.handlers.TriggerHandler;
import com.sx4.bot.http.HttpCallback;
import com.sx4.bot.paged.MessagePagedResult;
import com.sx4.bot.paged.PagedResult.SelectType;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.emoji.CustomEmoji;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.entities.emoji.EmojiUnion;
import net.dv8tion.jda.api.requests.Route;
import net.dv8tion.jda.internal.requests.RestActionImpl;
import net.dv8tion.jda.internal.utils.EncodingUtil;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.internal.http.HttpMethod;
import org.bson.Document;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class TriggerUtility {

	public static List<CompletableFuture<Void>> executeActions(Document trigger, Sx4 bot, FormatterManager manager, Message message) {
		GuildMessageChannel channel = message.getGuildChannel();

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
				case EXECUTE_COMMAND -> new ExecuteCommandTriggerAction(manager, actionData, (Sx4CommandListener) bot.getCommandListener(), message);
				case SEND_PAGED_MESSAGE -> new SendPagedMessageTriggerAction(bot, manager, actionData, channel, message.getAuthor());
			};

			if (actionData.containsKey("order")) {
				orderedFuture = orderedFuture.thenCompose($ -> action.run());
			} else {
				futures.add(action.run());
			}
		}

		futures.add(orderedFuture);

		return futures;
	}

	public static List<CompletableFuture<Void>> executeActions(Document trigger, Sx4 bot, Message message) {
		FormatterManager manager = FormatterManager.getDefaultManager()
			.addVariable("member", message.getMember())
			.addVariable("user", message.getAuthor())
			.addVariable("channel", message.getGuildChannel())
			.addVariable("server", message.getGuild())
			.addVariable("now", OffsetDateTime.now())
			.addVariable("random", new Random());

		return TriggerUtility.executeActions(trigger, bot, manager, message);
	}

	public static CompletableFuture<Void> executeRequest(FormatterManager manager, Document oldAction) {
		Document action = new JsonFormatter(oldAction, manager).parse();

		String body = action.getString("body");

		Request.Builder request;
		try {
			request = new Request.Builder()
				.url(RequestUtility.getWorkerUrl(action.getString("url")))
				.method(action.getString("method"), body == null ? null : RequestBody.create(body, MediaType.parse(action.getString("contentType"))));
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
			manager.addVariable(action.get("variable", "response"), new FormatterResponse(response));

			future.complete(null);
		});

		return future;
	}

	public static CompletableFuture<Void> sendMessage(FormatterManager manager, Document oldAction, GuildMessageChannel channel) {
		Document action = new JsonFormatter(oldAction, manager).parse();

		String channelId = action.getString("channelId");
		GuildMessageChannel messageChannel = channelId == null ? channel : channel.getGuild().getChannelById(GuildMessageChannel.class, channelId);
		if (messageChannel == null) {
			return CompletableFuture.failedFuture(new IllegalArgumentException("`channelId` supplied is not a valid channel"));
		}

		return messageChannel.sendMessage(MessageUtility.fromWebhookMessage(MessageUtility.fromJson(action.get("response", Document.class), true).build())).setAllowedMentions(EnumSet.allOf(Message.MentionType.class)).submit()
			.thenAccept(message -> manager.addVariable(action.get("variable", "message"), message));
	}

	public static CompletableFuture<Void> sendPagedMessage(Sx4 bot, FormatterManager manager, Document action, GuildMessageChannel channel, User owner) {
		String channelId = action.getString("channelId");
		GuildMessageChannel messageChannel = channelId == null ? channel : channel.getGuild().getChannelById(GuildMessageChannel.class, channelId);
		if (messageChannel == null) {
			return CompletableFuture.failedFuture(new IllegalArgumentException("`channelId` supplied is not a valid channel"));
		}

		Object listData = action.get("list");
		Object listObject = listData instanceof List ? listData : Formatter.getValue((String) listData, manager);
		if (!(listObject instanceof List list)) {
			return CompletableFuture.failedFuture(new IllegalArgumentException("`list` field didn't format to a list"));
		}

		boolean indexed = action.getBoolean("indexed", true);
		boolean increasedIndex = action.getBoolean("increasedIndex", true);
		String display = action.getString("display");
		Document selectData = action.get("select", Document.class);

		EnumSet<SelectType> types = EnumSet.noneOf(SelectType.class);
		if (selectData != null) {
			if (indexed || increasedIndex) {
				types.add(SelectType.INDEX);
			}

			types.add(SelectType.OBJECT);
		}

		MessagePagedResult<?> paged = new MessagePagedResult.Builder<Object>(bot, list)
			.setIndexed(indexed)
			.setIncreasedIndex(increasedIndex)
			.setAutoSelect(action.getBoolean("autoSelect", false))
			.setPerPage(action.getInteger("perPage", 10))
			.setSelect(types)
			.setDisplayFunction(object -> {
				if (display == null) {
					return object.toString();
				}

				manager.addVariable("this", object);
				String value = new StringFormatter(display, manager).parse();
				manager.removeVariable("this");

				return value;
			}).build();

		paged.onSelect(select -> {
			manager.addVariable("this", select.getSelected());
			Document message = new JsonFormatter(selectData, manager).parse();
			manager.removeVariable("this");

			messageChannel.sendMessage(MessageUtility.fromWebhookMessage(MessageUtility.fromJson(message, true).build())).queue();
		});

		paged.execute(messageChannel, owner);

		return CompletableFuture.completedFuture(null);
	}

	public static CompletableFuture<Void> addReaction(FormatterManager manager, Document oldAction, Message message) {
		Document action = new JsonFormatter(oldAction, manager).parse();

		String channelId = action.get("channelId", message.getChannel().getId());
		String messageId = action.get("messageId", message.getId());

		Document emote = action.get("emote", Document.class);
		String reactionCode = emote.containsKey("name") ? Emoji.fromUnicode(emote.getString("name")).getAsReactionCode() : EncodingUtil.encodeReaction("a:" + emote.getString("id"));

		Route.CompiledRoute route = Route.Messages.ADD_REACTION.compile(channelId, messageId, reactionCode, "@me");
		return new RestActionImpl<>(message.getJDA(), route).submit().thenApply($ -> null);
	}

	public static CompletableFuture<Void> executeCommand(FormatterManager manager, Document action, Sx4CommandListener listener, Message message) {
		String commandName = action.getString("command");
		String arguments = new StringFormatter(action.get("arguments", ""), manager).parse();

		Sx4Command command = listener.getAllCommands().stream()
			.filter(c -> c.getCommandTrigger().equals(commandName))
			.map(Sx4Command.class::cast)
			.findFirst()
			.orElse(null);

		if (command == null) {
			return CompletableFuture.failedFuture(new IllegalArgumentException("Command no longer exists?"));
		}

		List<ICommand> commands = new ArrayList<>();
		commands.add(command);
		commands.addAll(command.getDummyCommands());

		List<CommandListener.Failure> possibleCommands = new ArrayList<>();

		long nano = System.nanoTime();

		for (ICommand c : commands) {
			CommandEvent event;
			try {
				event = listener.getCommandParser().parse(listener, c, message, "", commandName, (arguments.isEmpty() ? "" : " ") + arguments, nano);
			} catch (ParseException e) {
				possibleCommands.add(new CommandListener.Failure(command, e));
				continue;
			}

			if (event != null) {
				listener.queueCommand(c, event, nano, event.getArguments());
				return CompletableFuture.completedFuture(null);
			}
		}

		if (possibleCommands.size() > 0) {
			listener.getMessageParseFailureFunction().accept(message, "", possibleCommands);
		}

		return CompletableFuture.completedFuture(null);
	}
	
	public static Document parseTriggerAction(Sx4CommandEvent event, TriggerActionType type, Document data) {
		Document action = new Document("type", type.getId());

		Object order = data.get("order");
		if (order instanceof Integer && (int) order >= 0) {
			action.append("order", order);
		}

		Object run = data.get("run");
		if (run instanceof String) {
			action.append("run", run);
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
			if (!MessageUtility.isValid((Document) response, false)) {
				throw new IllegalArgumentException("`response` field was not valid message json");
			}

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
			if (name == null) {
				throw new IllegalArgumentException("Command name must be given in the `command` field");
			}

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
			if (arguments != null && !(arguments instanceof String)) {
				throw new IllegalArgumentException("`arguments` field has to be a string");
			}

			if (arguments != null) {
				action.append("arguments", arguments);
			}
		} else if (type == TriggerActionType.SEND_PAGED_MESSAGE) {
			Object list = data.get("list");
			if (!(list instanceof List || list instanceof String)) {
				throw new IllegalArgumentException("`list` field has to be a list or a formatter converting to a list");
			}

			action.append("list", list);

			Object select = data.get("select");
			if (select != null && !(select instanceof Document)) {
				throw new IllegalArgumentException("`select` field has to be json");
			}

			if (select != null) {
				MessageUtility.removeFields((Document) select);
				if (!MessageUtility.isValid((Document) select, false)) {
					throw new IllegalArgumentException("`response` field was not valid message json");
				}

				action.append("select", select);
			}

			Object display = data.get("display");
			if (display != null && !(display instanceof String)) {
				throw new IllegalArgumentException("`display` field has to be a boolean");
			}

			if (display != null) {
				action.append("display", display);
			}

			Object autoSelect = data.get("autoSelect");
			if (autoSelect != null && !(autoSelect instanceof Boolean)) {
				throw new IllegalArgumentException("`autoSelect` field has to be a boolean");
			}

			if (autoSelect != null) {
				action.append("autoSelect", autoSelect);
			}

			Object indexed = data.get("indexed");
			if (indexed != null && !(indexed instanceof Boolean)) {
				throw new IllegalArgumentException("`indexed` field has to be a boolean");
			}

			if (indexed != null) {
				action.append("indexed", indexed);
			}

			Object increasedIndex = data.get("increasedIndex");
			if (increasedIndex != null && !(increasedIndex instanceof Boolean)) {
				throw new IllegalArgumentException("`increasedIndex` field has to be a boolean");
			}

			if (increasedIndex != null) {
				action.append("increasedIndex", increasedIndex);
			}

			Object perPage = data.get("perPage");
			if (perPage != null && !(perPage instanceof Integer)) {
				throw new IllegalArgumentException("`perPage` field has to be an integer");
			}

			if (perPage != null) {
				action.append("perPage", perPage);
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
		} else {
			throw new IllegalArgumentException("That action type is not supported yet");
		}

		return action;
	}

}
