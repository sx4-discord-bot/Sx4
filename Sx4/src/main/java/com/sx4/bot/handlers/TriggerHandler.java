package com.sx4.bot.handlers;

import com.mongodb.client.ChangeStreamIterable;
import com.mongodb.client.model.*;
import com.mongodb.client.model.changestream.ChangeStreamDocument;
import com.mongodb.client.model.changestream.FullDocument;
import com.mongodb.client.model.changestream.FullDocumentBeforeChange;
import com.mongodb.client.model.changestream.OperationType;
import com.sx4.bot.core.Sx4;
import com.sx4.bot.database.mongo.MongoDatabase;
import com.sx4.bot.database.mongo.model.AggregateOperators;
import com.sx4.bot.database.mongo.model.Operators;
import com.sx4.bot.entities.interaction.ButtonType;
import com.sx4.bot.entities.trigger.TriggerEventType;
import com.sx4.bot.formatter.input.InputFormatter;
import com.sx4.bot.formatter.input.InputFormatterContext;
import com.sx4.bot.formatter.output.FormatterManager;
import com.sx4.bot.utility.ExceptionUtility;
import com.sx4.bot.utility.FutureUtility;
import com.sx4.bot.utility.TriggerUtility;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.MessageUpdateEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import okhttp3.OkHttpClient;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.jetbrains.annotations.NotNull;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.*;

public class TriggerHandler implements EventListener {

	public final static OkHttpClient CLIENT = new OkHttpClient.Builder()
		.callTimeout(5, TimeUnit.SECONDS)
		.build();

	private final Sx4 bot;

	public TriggerHandler(Sx4 bot) {
		this.bot = bot;

		List<Bson> pipeline = List.of(
			Aggregates.match(Filters.or(Filters.eq("operationType", "insert"), Filters.eq("operationType", "delete")))
		);

		ChangeStreamIterable<Document> stream = this.bot.getMongo().getTriggers()
			.watch(pipeline)
			.fullDocument(FullDocument.WHEN_AVAILABLE)
			.fullDocumentBeforeChange(FullDocumentBeforeChange.WHEN_AVAILABLE);

		ExecutorService executor = Executors.newSingleThreadExecutor();
		executor.submit(() -> stream.forEach(this::onTriggerChange));
	}

	public void onTriggerChange(ChangeStreamDocument<Document> stream) {
		boolean delete = stream.getOperationType() == OperationType.DELETE;

		Document data = delete ? stream.getFullDocumentBeforeChange() : stream.getFullDocument();
		if (data == null) {
			return;
		}

		Document template = data.get("template", Document.class);
		if (template == null) {
			return;
		}

		this.bot.getMongo().updateTriggerTemplate(Filters.eq("_id", template.getObjectId("id")), Updates.inc("uses", delete ? -1 : 1));
	}

	public void handle(Message message) {
		if (!message.isFromGuild()) {
			return;
		}

		User author = message.getAuthor();
		if (author.isBot()) {
			return;
		}

		Guild guild = message.getGuild();
		GuildMessageChannel channel = message.getGuildChannel();
		if (!guild.getSelfMember().hasPermission(channel, Permission.MESSAGE_SEND)) {
			return;
		}

		List<Bson> guildsPipeline = List.of(
			Aggregates.project(Projections.include("prefixes")),
			Aggregates.match(Filters.eq("_id", guild.getIdLong())),
			Aggregates.group(null, Accumulators.first("guildPrefixes", "$prefixes"))
		);

		List<Bson> usersPipeline = List.of(
			Aggregates.project(Projections.include("prefixes")),
			Aggregates.match(Filters.eq("_id", message.getAuthor().getIdLong())),
			Aggregates.group(null, Accumulators.first("userPrefixes", "$prefixes"))
		);

		List<Bson> pipeline = List.of(
			Aggregates.match(Filters.and(Filters.eq("type", TriggerEventType.MESSAGE_MATCHED.getId()), Filters.eq("guildId", guild.getIdLong()))),
			Aggregates.group(null, Accumulators.push("triggers", Operators.ROOT)),
			Aggregates.unionWith("users", usersPipeline),
			Aggregates.unionWith("guilds", guildsPipeline),
			AggregateOperators.mergeFields("triggers", "guildPrefixes", "userPrefixes")
		);

		this.bot.getMongo().aggregateTriggers(pipeline).whenComplete((documents, databaseException) -> {
			this.bot.getExecutor().submit(() -> {
				if (ExceptionUtility.sendErrorMessage(databaseException)) {
					return;
				}

				if (documents.isEmpty()) {
					return;
				}

				Document data = documents.get(0);

				List<String> userPrefixes = data.getList("userPrefixes", String.class, Collections.emptyList());
				List<String> guildPrefixes = data.getList("guildPrefixes", String.class, Collections.emptyList());
				List<String> prefixes = userPrefixes.isEmpty() && guildPrefixes.isEmpty() ? this.bot.getConfig().getDefaultPrefixes() : userPrefixes.isEmpty() ? guildPrefixes : userPrefixes;

				// Ensure bot mention always works
				long selfId = message.getJDA().getSelfUser().getIdLong();
				prefixes.add("<@" + selfId + "> ");
				prefixes.add("<@!" + selfId + "> ");

				InputFormatterContext context = new InputFormatterContext(message);
				context.setVariable("prefixes", prefixes);

				List<Document> triggers = data.getList("triggers", Document.class, Collections.emptyList());
				triggers.forEach(trigger -> {
					if (!trigger.get("enabled", true)) {
						return;
					}

					FormatterManager manager;
					if (trigger.get("format", true)) {
						manager = FormatterManager.getDefaultManager()
							.addVariable("member", message.getMember())
							.addVariable("user", message.getAuthor())
							.addVariable("channel", channel)
							.addVariable("server", guild)
							.addVariable("now", OffsetDateTime.now())
							.addVariable("random", new Random());
					} else {
						manager = new FormatterManager();
					}

					InputFormatter formatter = new InputFormatter(trigger.getString("trigger"));

					List<Object> arguments;
					try {
						arguments = formatter.parse(context, message.getContentRaw(), trigger.get("case", false));
					} catch (IllegalArgumentException e) {
						channel.sendMessage(e.getMessage() + " " + this.bot.getConfig().getFailureEmote()).queue();
						return;
					} catch (Throwable exception) {
						ExceptionUtility.sendExceptionally(channel, exception);
						return;
					}

					if (arguments == null) {
						return;
					}

					for (int i = 0; i < arguments.size(); i++) {
						manager.addVariable(String.valueOf(i), arguments.get(i));
					}

					List<Document> variables = trigger.getList("variables", Document.class, Collections.emptyList());
					for (Document variable : variables) {
						manager.addVariable(variable.getString("key"), variable.get("value"));
					}

					List<CompletableFuture<Void>> futures = TriggerUtility.executeActions(trigger, this.bot, manager, message);

					FutureUtility.allOf(futures).whenComplete(($, exception) -> {
						Throwable cause = exception instanceof CompletionException ? exception.getCause() : exception;
						if (cause instanceof IllegalArgumentException) {
							this.bot.getMongo().updateTrigger(Filters.eq("_id", trigger.getObjectId("_id")), Updates.set("enabled", false), new UpdateOptions()).whenComplete(MongoDatabase.exceptionally());
						}

						ExceptionUtility.sendExceptionally(channel, exception);
					});
				});
			});
		});
	}

	public void handleButton(ButtonInteractionEvent event) {
		Button button = event.getButton();
		if (!event.isFromGuild() || button.isDisabled()) {
			return;
		}

		Guild guild = event.getGuild();
		Message message = event.getMessage();
		MessageChannel channel = event.getChannel();

		List<Bson> pipeline = List.of(
			Aggregates.match(Filters.and(Filters.eq("type", TriggerEventType.BUTTON_CLICKED.getId()), Filters.eq("guildId", guild.getIdLong()))),
			Aggregates.group(null, Accumulators.push("triggers", Operators.ROOT))
		);

		this.bot.getMongo().aggregateTriggers(pipeline).whenComplete((documents, databaseException) -> {
			if (ExceptionUtility.sendErrorMessage(databaseException)) {
				return;
			}

			if (documents.isEmpty()) {
				return;
			}

			Document data = documents.get(0);

			InputFormatterContext context = new InputFormatterContext(message);

			List<Document> triggers = data.getList("triggers", Document.class, Collections.emptyList());
			triggers.forEach(trigger -> {
				if (!trigger.get("enabled", true)) {
					return;
				}

				FormatterManager manager;
				if (trigger.get("format", true)) {
					manager = FormatterManager.getDefaultManager()
						.addVariable("member", message.getMember())
						.addVariable("user", message.getAuthor())
						.addVariable("channel", channel)
						.addVariable("server", guild)
						.addVariable("now", OffsetDateTime.now())
						.addVariable("random", new Random());
				} else {
					manager = new FormatterManager();
				}

				InputFormatter formatter = new InputFormatter(ButtonType.TRIGGER_COMPONENT_CLICKED.getId() + ":" + trigger.getString("trigger"));

				List<Object> arguments;
				try {
					arguments = formatter.parse(context, button.getId(), trigger.get("case", false));
				} catch (IllegalArgumentException e) {
					channel.sendMessage(e.getMessage() + " " + this.bot.getConfig().getFailureEmote()).queue();
					return;
				} catch (Throwable exception) {
					ExceptionUtility.sendExceptionally(channel, exception);
					return;
				}

				if (arguments == null) {
					return;
				}

				for (int i = 0; i < arguments.size(); i++) {
					manager.addVariable(String.valueOf(i), arguments.get(i));
				}

				List<Document> variables = trigger.getList("variables", Document.class, Collections.emptyList());
				for (Document variable : variables) {
					manager.addVariable(variable.getString("key"), variable.get("value"));
				}

				List<CompletableFuture<Void>> futures = TriggerUtility.executeActions(trigger, this.bot, manager, message, event);

				FutureUtility.allOf(futures).whenComplete(($, exception) -> {
					Throwable cause = exception instanceof CompletionException ? exception.getCause() : exception;
					if (cause instanceof IllegalArgumentException) {
						this.bot.getMongo().updateTrigger(Filters.eq("_id", trigger.getObjectId("_id")), Updates.set("enabled", false), new UpdateOptions()).whenComplete(MongoDatabase.exceptionally());
					}

					ExceptionUtility.sendExceptionally(channel, exception);
				});
			});
		});
	}

	@Override
	public void onEvent(@NotNull GenericEvent event) {
		if (event instanceof MessageReceivedEvent) {
			this.handle(((MessageReceivedEvent) event).getMessage());
		} else if (event instanceof MessageUpdateEvent) {
			this.handle(((MessageUpdateEvent) event).getMessage());
		} else if (event instanceof ButtonInteractionEvent) {
			this.handleButton((ButtonInteractionEvent) event);
		}
	}

}
