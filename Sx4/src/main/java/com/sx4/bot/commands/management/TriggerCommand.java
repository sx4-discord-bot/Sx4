package com.sx4.bot.commands.management;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.command.Command;
import com.jockie.bot.core.option.Option;
import com.mongodb.ErrorCategory;
import com.mongodb.MongoWriteException;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.sx4.bot.annotations.argument.AdvancedMessage;
import com.sx4.bot.annotations.argument.AlternativeOptions;
import com.sx4.bot.annotations.argument.DefaultString;
import com.sx4.bot.annotations.argument.Options;
import com.sx4.bot.annotations.command.AuthorPermissions;
import com.sx4.bot.annotations.command.BotPermissions;
import com.sx4.bot.annotations.command.CommandId;
import com.sx4.bot.annotations.command.Examples;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.database.mongo.MongoDatabase;
import com.sx4.bot.database.mongo.model.Operators;
import com.sx4.bot.entities.argument.Alternative;
import com.sx4.bot.formatter.FormatterManager;
import com.sx4.bot.formatter.JsonFormatter;
import com.sx4.bot.formatter.function.FormatterVariable;
import com.sx4.bot.paged.PagedResult;
import com.sx4.bot.utility.ExceptionUtility;
import com.sx4.bot.utility.MessageUtility;
import com.sx4.bot.utility.StringUtility;
import com.sx4.bot.waiter.Waiter;
import com.sx4.bot.waiter.exception.CancelException;
import com.sx4.bot.waiter.exception.TimeoutException;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.interactions.components.Button;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.StringJoiner;
import java.util.concurrent.CompletionException;

public class TriggerCommand extends Sx4Command {

	public TriggerCommand() {
		super("trigger", 214);

		super.setDescription("Set up triggers to respond to certain words or phrases");
		super.setExamples("trigger toggle", "trigger add", "trigger remove");
		super.setCategoryAll(ModuleCategory.MANAGEMENT);
	}

	public void onCommand(Sx4CommandEvent event) {
		event.replyHelp().queue();
	}

	@Command(value="state", description="Updates the state of a trigger to enable or disable it")
	@CommandId(215)
	@Examples({"trigger state 6006ff6b94c9ed0f764ada83", "trigger state disable all", "trigger state enable 6006ff6b94c9ed0f764ada83"})
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	public void state(Sx4CommandEvent event, @Argument(value="state") @DefaultString("toggle") @Options({"enable", "toggle", "disable"}) String state, @Argument(value="id") Alternative<ObjectId> option) {
		Bson guildFilter = Filters.eq("guildId", event.getGuild().getIdLong());
		Bson filter = option.isAlternative() ? guildFilter : Filters.and(Filters.eq("_id", option.getValue()), guildFilter);
		List<Bson> update = List.of(Operators.set("enabled", state.equals("toggle") ? Operators.cond(Operators.exists("$enabled"), false, Operators.REMOVE) : state.equals("enable") ? Operators.REMOVE : false));

		event.getMongo().updateManyTriggers(filter, update, new UpdateOptions()).whenComplete((result, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			if (result.getMatchedCount() == 0) {
				event.replyFailure(option.isAlternative() ? "There are no triggers in this server" : "I could not find that trigger").queue();
				return;
			}

			long modified = result.getModifiedCount();
			if (modified == 0) {
				event.replyFailure((option.isAlternative() ? "All triggers were" : "That trigger was") +  " already in that state").queue();
				return;
			}

			event.replySuccess((option.isAlternative() ? String.format("**%,d** trigger%s ", modified, modified == 1 ? " has had its" : "s have had their") : "That trigger has had its ") + "state updated").queue();
		});
	}

	@Command(value="add", description="Add a trigger to the server")
	@CommandId(216)
	@Examples({"trigger add hi Hello", "trigger add \"some word\" some other words"})
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	public void add(Sx4CommandEvent event, @Argument(value="trigger") String trigger, @Argument(value="response", endless=true) String response) {
		Document data = new Document("trigger", trigger)
			.append("response", new Document("content", response))
			.append("guildId", event.getGuild().getIdLong());

		event.getMongo().insertTrigger(data).whenComplete((result, exception) -> {
			Throwable cause = exception instanceof CompletionException ? exception.getCause() : exception;
			if (cause instanceof MongoWriteException && ((MongoWriteException) cause).getError().getCategory() == ErrorCategory.DUPLICATE_KEY) {
				event.replyFailure("You already have a trigger with that content").queue();
				return;
			}

			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			event.replySuccess("That trigger has been added with id `" + result.getInsertedId().asObjectId().getValue().toHexString() + "`").queue();
		});
	}

	@Command(value="advanced add", description="Same as `trigger add` but takes a json message")
	@CommandId(220)
	@Examples({"trigger advanced add hi {\"embed\": {\"description\": \"Hello\"}}", "trigger advanced add \"some word\" {\"embed\": {\"description\": \"some other words\"}}"})
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	public void advancedAdd(Sx4CommandEvent event, @Argument(value="trigger") String trigger, @Argument(value="response", endless=true) @AdvancedMessage Document response) {
		Document data = new Document("trigger", trigger)
			.append("response", response)
			.append("guildId", event.getGuild().getIdLong());

		event.getMongo().insertTrigger(data).whenComplete((result, exception) -> {
			Throwable cause = exception instanceof CompletionException ? exception.getCause() : exception;
			if (cause instanceof MongoWriteException && ((MongoWriteException) cause).getError().getCategory() == ErrorCategory.DUPLICATE_KEY) {
				event.replyFailure("You already have a trigger with that content").queue();
				return;
			}

			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			event.replySuccess("That trigger has been added with id `" + result.getInsertedId().asObjectId().getValue().toHexString() + "`").queue();
		});
	}

	@Command(value="edit", description="Edit a trigger in the server")
	@CommandId(217)
	@Examples({"trigger edit 6006ff6b94c9ed0f764ada83 Hello!", "trigger edit 6006ff6b94c9ed0f764ada83 some other words was not enough --append"})
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	public void edit(Sx4CommandEvent event, @Argument(value="id") ObjectId id, @Argument(value="response", endless=true) String response, @Option(value="append", description="Appends the response to the current one") boolean append) {
		List<Bson> update = List.of(Operators.set("response", new Document("content", Operators.cond(Operators.and(Operators.exists("$response.content"), append), Operators.concat("$response.content", response), response))));
		event.getMongo().updateTrigger(Filters.and(Filters.eq("_id", id), Filters.eq("guildId", event.getGuild().getIdLong())), update, new UpdateOptions()).whenComplete((result, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			if (result.getMatchedCount() == 0) {
				event.replyFailure("I could not find that trigger").queue();
				return;
			}

			if (result.getModifiedCount() == 0) {
				event.replyFailure("That trigger response was already set to that").queue();
				return;
			}

			event.replySuccess("That trigger has been edited").queue();
		});
	}

	@Command(value="advanced edit", description="Same as `trigger edit` but takes a json message")
	@CommandId(221)
	@Examples({"trigger advanced edit 6006ff6b94c9ed0f764ada83 {\"embed\": {\"description\": \"Hello!\"}}", "trigger advanced edit 6006ff6b94c9ed0f764ada83 {\"embed\": {\"description\": \"some other words was not enough\"}}"})
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	public void advancedEdit(Sx4CommandEvent event, @Argument(value="id") ObjectId id, @Argument(value="response", endless=true) @AdvancedMessage Document response) {
		event.getMongo().updateTrigger(Filters.and(Filters.eq("_id", id), Filters.eq("guildId", event.getGuild().getIdLong())), Updates.set("response", response), new UpdateOptions()).whenComplete((result, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			if (result.getMatchedCount() == 0) {
				event.replyFailure("I could not find that trigger").queue();
				return;
			}

			if (result.getModifiedCount() == 0) {
				event.replyFailure("That trigger response was already set to that").queue();
				return;
			}

			event.replySuccess("That trigger has been edited").queue();
		});
	}

	@Command(value="delete", aliases={"remove"}, description="Deletes a trigger in the server")
	@CommandId(218)
	@Examples({"trigger delete 6006ff6b94c9ed0f764ada83", "trigger delete all"})
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	public void delete(Sx4CommandEvent event, @Argument(value="id | all") @AlternativeOptions("all") Alternative<ObjectId> option) {
		if (option.isAlternative()) {
			List<Button> buttons = List.of(Button.success("yes", "Yes"), Button.danger("no", "No"));

			event.reply(event.getAuthor().getName() + ", are you sure you want to delete **all** the triggers in this server?").setActionRow(buttons).submit()
				.thenCompose(message -> {
					return new Waiter<>(event.getBot(), ButtonClickEvent.class)
						.setPredicate(e -> {
							Button button = e.getButton();
							return button != null && button.getId().equals("yes") && e.getMessageIdLong() == message.getIdLong() && e.getUser().getIdLong() == event.getAuthor().getIdLong();
						})
						.setCancelPredicate(e -> {
							Button button = e.getButton();
							return button != null && button.getId().equals("no") && e.getMessageIdLong() == message.getIdLong() && e.getUser().getIdLong() == event.getAuthor().getIdLong();
						})
						//.setRunAfter(e -> e.deferEdit().queue())
						.setTimeout(60)
						.start();
				})
				.thenCompose(messageEvent -> event.getMongo().deleteManyTriggers(Filters.eq("guildId", event.getGuild().getIdLong())))
				.whenComplete((result, exception) -> {
					Throwable cause = exception instanceof CompletionException ? exception.getCause() : exception;
					if (cause instanceof CancelException) {
						event.replySuccess("Cancelled").queue();
						return;
					} else if (cause instanceof TimeoutException) {
						event.reply("Timed out :stopwatch:").queue();
						return;
					} else if (ExceptionUtility.sendExceptionally(event, cause)) {
						return;
					}

					if (result.getDeletedCount() == 0) {
						event.replySuccess("There are no triggers in this server").queue();
						return;
					}

					event.replySuccess("All triggers have been deleted in this server").queue();
				});
		} else {
			event.getMongo().deleteTrigger(Filters.and(Filters.eq("_id", option.getValue()), Filters.eq("guildId", event.getGuild().getIdLong()))).whenComplete((result, exception) -> {
				if (ExceptionUtility.sendExceptionally(event, exception)) {
					return;
				}

				if (result.getDeletedCount() == 0) {
					event.replyFailure("I could not find that trigger").queue();
					return;
				}

				event.replySuccess("That trigger has been deleted").queue();
			});
		}
	}

	@Command(value="case", aliases={"case sensitive"}, description="Enables or disables whether a trigger should be case sensitive")
	@CommandId(219)
	@Examples({"trigger case 6006ff6b94c9ed0f764ada83", "trigger case disable 6006ff6b94c9ed0f764ada83", "trigger case enable all"})
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	public void caseSensitive(Sx4CommandEvent event, @Argument(value="state") @DefaultString("toggle") @Options({"enable", "disable", "toggle"}) String state, @Argument(value="id") @AlternativeOptions("all") Alternative<ObjectId> option) {
		Bson guildFilter = Filters.eq("guildId", event.getGuild().getIdLong());
		Bson filter = option.isAlternative() ? guildFilter : Filters.and(Filters.eq("_id", option.getValue()), guildFilter);
		List<Bson> update = List.of(Operators.set("case", state.equals("toggle") ? Operators.cond("$case", Operators.REMOVE, true) : state.equals("enable") ? true : Operators.REMOVE));

		event.getMongo().updateManyTriggers(filter, update, new UpdateOptions()).whenComplete((result, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			if (result.getMatchedCount() == 0) {
				event.replyFailure(option.isAlternative() ? "There are no triggers in this server" : "I could not find that trigger").queue();
				return;
			}

			long modified = result.getModifiedCount();
			if (modified == 0) {
				event.replyFailure((option.isAlternative() ? "All triggers were" : "That trigger was") +  " already in that state for case sensitivity").queue();
				return;
			}

			event.replySuccess((option.isAlternative() ? String.format("**%,d** trigger%s ", modified, modified == 1 ? " has had its" : "s have had their") : "That trigger has had its ") + "case sensitivity updated").queue();
		});
	}

	@Command(value="preview", description="Preview what a trigger will look like")
	@CommandId(222)
	@Examples({"trigger preview 600968f92850ef72c9af8756"})
	public void preview(Sx4CommandEvent event, @Argument(value="id") ObjectId id, @Option(value="raw", description="Returns the raw version of the trigger") boolean raw) {
		Document trigger = event.getMongo().getTrigger(Filters.and(Filters.eq("_id", id), Filters.eq("guildId", event.getGuild().getIdLong())), Projections.include("enabled", "response"));
		if (trigger == null) {
			event.replyFailure("I could not find that trigger").queue();
			return;
		}

		if (!trigger.get("enabled", true)) {
			event.replyFailure("That trigger is not enabled").queue();
			return;
		}

		if (raw) {
			event.replyFile(trigger.get("response", Document.class).toJson(MongoDatabase.PRETTY_JSON).getBytes(StandardCharsets.UTF_8), "trigger.json").queue();
		} else {
			Document response = new JsonFormatter(trigger.get("response", Document.class))
				.member(event.getMember())
				.user(event.getAuthor())
				.channel(event.getTextChannel())
				.guild(event.getGuild())
				.addVariable("now", OffsetDateTime.now())
				.addVariable("random", new Random())
				.parse();

			try {
				MessageUtility.fromWebhookMessage(event.getChannel(), MessageUtility.fromJson(response).build()).queue();
			} catch (IllegalArgumentException e) {
				event.replyFailure(e.getMessage()).queue();
			}
		}
	}

	@Command(value="formatters", aliases={"format", "formatting"}, description="Get all the formatters for triggers you can use")
	@CommandId(443)
	@Examples({"trigger formatters"})
	@BotPermissions(permissions={Permission.MESSAGE_EMBED_LINKS})
	public void formatters(Sx4CommandEvent event) {
		EmbedBuilder embed = new EmbedBuilder()
			.setAuthor("Trigger Formatters", null, event.getSelfUser().getEffectiveAvatarUrl());

		FormatterManager manager = FormatterManager.getDefaultManager();

		StringJoiner content = new StringJoiner("\n");
		for (FormatterVariable<?> variable : manager.getVariables(User.class)) {
			content.add("`{user." + variable.getName() + "}` - " + variable.getDescription());
		}

		for (FormatterVariable<?> variable : manager.getVariables(Member.class)) {
			content.add("`{member." + variable.getName() + "}` - " + variable.getDescription());
		}

		for (FormatterVariable<?> variable : manager.getVariables(Guild.class)) {
			content.add("`{server." + variable.getName() + "}` - " + variable.getDescription());
		}

		for (FormatterVariable<?> variable : manager.getVariables(TextChannel.class)) {
			content.add("`{channel." + variable.getName() + "}` - " + variable.getDescription());
		}

		for (FormatterVariable<?> variable : manager.getVariables(OffsetDateTime.class)) {
			content.add("`{now." + variable.getName() + "}` - " + variable.getDescription());
		}

		embed.setDescription(content.toString());

		event.reply(embed.build()).queue();
	}

	@Command(value="list", description="List the triggers in the current server")
	@CommandId(253)
	@Examples({"trigger list"})
	@BotPermissions(permissions={Permission.MESSAGE_EMBED_LINKS})
	public void list(Sx4CommandEvent event) {
		List<Document> triggers = event.getMongo().getTriggers(Filters.eq("guildId", event.getGuild().getIdLong()), Projections.include("trigger")).into(new ArrayList<>());
		if (triggers.isEmpty()) {
			event.replyFailure("There are no triggers setup in this server").queue();
			return;
		}

		PagedResult<Document> paged = new PagedResult<>(event.getBot(), triggers)
			.setAuthor("Triggers", null, event.getGuild().getIconUrl())
			.setIndexed(false)
			.setSelect()
			.setDisplayFunction(data -> "`" + data.getObjectId("_id").toHexString() + "` - " + StringUtility.limit(data.getString("trigger"), MessageEmbed.DESCRIPTION_MAX_LENGTH / 10 - 29, "..."));

		paged.execute(event);
	}

}