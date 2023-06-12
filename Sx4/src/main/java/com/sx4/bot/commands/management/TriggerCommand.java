package com.sx4.bot.commands.management;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.command.Command;
import com.jockie.bot.core.option.Option;
import com.mongodb.ErrorCategory;
import com.mongodb.MongoWriteException;
import com.mongodb.client.model.*;
import com.sx4.bot.annotations.argument.*;
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
import com.sx4.bot.entities.interaction.ButtonType;
import com.sx4.bot.entities.interaction.CustomButtonId;
import com.sx4.bot.entities.trigger.TriggerActionType;
import com.sx4.bot.formatter.exception.FormatterException;
import com.sx4.bot.formatter.input.InputFormatter;
import com.sx4.bot.formatter.output.FormatterManager;
import com.sx4.bot.formatter.output.function.FormatterVariable;
import com.sx4.bot.paged.PagedResult;
import com.sx4.bot.utility.ExceptionUtility;
import com.sx4.bot.utility.FutureUtility;
import com.sx4.bot.utility.StringUtility;
import com.sx4.bot.utility.TriggerUtility;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.StringJoiner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;

public class TriggerCommand extends Sx4Command {

	public static final int MAX_ACTIONS = 3;

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
	public void state(Sx4CommandEvent event, @Argument(value="state") @DefaultString("toggle") @Options({"enable", "toggle", "disable"}) String state, @Argument(value="id") @AlternativeOptions("all") Alternative<ObjectId> option) {
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
	public void add(Sx4CommandEvent event, @Argument(value="trigger") String trigger, @Argument(value="response", endless=true) String response, @Option(value="order", description="The order index of when the message is sent") @Limit(min=0) @DefaultNumber(-1) int order) {
		Document action = new Document("type", TriggerActionType.SEND_MESSAGE.getId()).append("response", new Document("content", response));
		if (order != -1) {
			action.append("order", order);
		}

		InputFormatter formatter = new InputFormatter(trigger);
		try {
			formatter.getNodes();
		} catch (FormatterException exception) {
			event.replyFailure(exception.getMessage()).queue();
			return;
		}

		Document data = new Document("trigger", trigger)
			.append("guildId", event.getGuild().getIdLong())
			.append("actions", List.of(action));

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
	public void advancedAdd(Sx4CommandEvent event, @Argument(value="trigger") String trigger, @Argument(value="response", endless=true) @AdvancedMessage Document response, @Option(value="order", description="The order index of when the message is sent") @Limit(min=0) @DefaultNumber(-1) int order) {
		Document action = new Document("type", TriggerActionType.SEND_MESSAGE.getId()).append("response", new Document("content", response));
		if (order != -1) {
			action.append("order", order);
		}

		InputFormatter formatter = new InputFormatter(trigger);
		try {
			formatter.getNodes();
		} catch (FormatterException exception) {
			event.replyFailure(exception.getMessage()).queue();
			return;
		}

		Document data = new Document("trigger", trigger)
			.append("guildId", event.getGuild().getIdLong())
			.append("actions", List.of(action));

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
		int type = TriggerActionType.SEND_MESSAGE.getId();
		List<Bson> update = List.of(Operators.set("actions", Operators.let(new Document("action", Operators.first(Operators.filter("$actions", Operators.eq("$$this.type", type)))), Operators.cond(Operators.isNull("$$action"), "$actions", Operators.concatArrays(Operators.filter("$actions", Operators.ne("$$this.type", type)), Operators.let(new Document("content", Operators.get("$$action", "response.content")), List.of(Operators.mergeObjects("$$action", new Document("response", new Document("content", Operators.cond(Operators.and(Operators.nonNull("$$content"), append), Operators.concat("$$content", response), response)))))))))));

		FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().projection(Projections.include("actions")).returnDocument(ReturnDocument.BEFORE);
		event.getMongo().findAndUpdateTrigger(Filters.and(Filters.eq("_id", id), Filters.eq("guildId", event.getGuild().getIdLong())), update, options).whenComplete((data, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			if (data == null) {
				event.replyFailure("I could not find that trigger").queue();
				return;
			}

			Document action = data.getList("actions", Document.class).stream()
				.filter(d -> d.getInteger("type") == type)
				.findFirst()
				.orElse(null);

			if (action == null) {
				event.replyFailure("That trigger does not have a `" + TriggerActionType.SEND_MESSAGE + "` action").queue();
				return;
			}

			String content = action.getEmbedded(List.of("response", "content"), String.class);
			if (content != null && content.equals(response)) {
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
		int type = TriggerActionType.SEND_MESSAGE.getId();
		List<Bson> update = List.of(Operators.set("actions", Operators.let(new Document("action", Operators.first(Operators.filter("$actions", Operators.eq("$$this.type", type)))), Operators.cond(Operators.isNull("$$action"), "$actions", Operators.concatArrays(Operators.filter("$actions", Operators.ne("$$this.type", type)), Operators.let(new Document("content", Operators.get("$$action", "response.content")), List.of(Operators.mergeObjects("$$action", new Document("response", response)))))))));

		FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().projection(Projections.include("actions")).returnDocument(ReturnDocument.BEFORE);
		event.getMongo().findAndUpdateTrigger(Filters.and(Filters.eq("_id", id), Filters.eq("guildId", event.getGuild().getIdLong())), update, options).whenComplete((data, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			if (data == null) {
				event.replyFailure("I could not find that trigger").queue();
				return;
			}

			Document action = data.getList("actions", Document.class).stream()
				.filter(d -> d.getInteger("type") == type)
				.findFirst()
				.orElse(null);

			if (action == null) {
				event.replyFailure("That trigger does not have a `" + TriggerActionType.SEND_MESSAGE + "` action").queue();
				return;
			}

			if (action.get("response", Document.class).equals(response)) {
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
			String acceptId = new CustomButtonId.Builder()
				.setType(ButtonType.TRIGGER_DELETE_CONFIRM)
				.setOwners(event.getAuthor().getIdLong())
				.setTimeout(60)
				.getId();

			String rejectId = new CustomButtonId.Builder()
				.setType(ButtonType.GENERIC_REJECT)
				.setOwners(event.getAuthor().getIdLong())
				.setTimeout(60)
				.getId();

			List<Button> buttons = List.of(Button.success(acceptId, "Yes"), Button.danger(rejectId, "No"));

			event.reply(event.getAuthor().getName() + ", are you sure you want to delete **all** the triggers in this server?")
				.setActionRow(buttons)
				.queue();
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
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	public void preview(Sx4CommandEvent event, @Argument(value="id") ObjectId id, @Option(value="raw", description="Returns the raw version of the trigger actions") boolean raw) {
		Document trigger = event.getMongo().getTrigger(Filters.and(Filters.eq("_id", id), Filters.eq("guildId", event.getGuild().getIdLong())), Projections.include("enabled", "actions"));
		if (trigger == null) {
			event.replyFailure("I could not find that trigger").queue();
			return;
		}

		if (!trigger.get("enabled", true)) {
			event.replyFailure("That trigger is not enabled").queue();
			return;
		}

		if (raw) {
			List<Document> actions = trigger.getList("actions", Document.class);

			String actionsContent = actions.stream()
				.sorted(Comparator.comparingInt(action -> action.getInteger("order", -1)))
				.map(action -> action.append("type", TriggerActionType.fromId(action.getInteger("type")).toString()))
				.map(action -> action.toJson(MongoDatabase.PRETTY_JSON).lines().map(line -> "    " + line).collect(Collectors.joining("\n")))
				.collect(Collectors.joining(",\n"));

			event.replyFile(("[\n" + actionsContent + "\n]").getBytes(StandardCharsets.UTF_8), "actions.json").queue();
		} else {
			List<CompletableFuture<Void>> futures = TriggerUtility.executeActions(trigger, event.getBot(), event.getMessage());

			FutureUtility.allOf(futures).whenComplete(($, exception) -> {
				Throwable cause = exception instanceof CompletionException ? exception.getCause() : exception;
				if (cause instanceof IllegalArgumentException) {
					event.replyFailure(cause.getMessage()).queue();
					return;
				}

				ExceptionUtility.sendExceptionally(event.getChannel(), exception);
			});
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

		for (FormatterVariable<?> variable : manager.getVariables(Message.class)) {
			content.add("`{message." + variable.getName() + "}` - " + variable.getDescription());
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

	public static class ActionCommand extends Sx4Command {

		public ActionCommand() {
			super("action", 485);

			super.setDescription("Allows you to perform an action when a trigger is triggers");
			super.setExamples("trigger action add", "trigger action remove");
			super.setCanaryCommand(true);
		}

		public void onCommand(Sx4CommandEvent event) {
			event.replyHelp().queue();
		}

		@Command(value="add", description="Adds an action to a trigger")
		@CommandId(486)
		@Examples({"trigger action add 600968f92850ef72c9af8756 request {\"url\": \"https://api.exchangerate.host/latest?base=EUR\", \"method\": \"GET\"}"})
		@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
		public void add(Sx4CommandEvent event, @Argument(value="id") ObjectId id, @Argument(value="type") TriggerActionType type, @Argument(value="json", endless=true) Document data) {
			Document action;
			try {
				action = TriggerUtility.parseTriggerAction(event, type, data);
			} catch (IllegalArgumentException e) {
				event.replyFailure(e.getMessage()).queue();
				return;
			}

			List<Bson> update = List.of(Operators.set("actions", Operators.cond(Operators.or(Operators.gte(Operators.size("$actions"), TriggerCommand.MAX_ACTIONS), Operators.gte(Operators.size(Operators.filter("$actions", Operators.eq("$$this.type", type.getId()))), type.getMaxActions())), "$actions", Operators.concatArrays("$actions", List.of(action)))));

			FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().projection(Projections.include("actions")).returnDocument(ReturnDocument.BEFORE);
			event.getMongo().findAndUpdateTrigger(Filters.and(Filters.eq("_id", id), Filters.eq("guildId", event.getGuild().getIdLong())), update, options).whenComplete((oldData, exception) -> {
				if (ExceptionUtility.sendExceptionally(event, exception)) {
					return;
				}

				if (oldData == null) {
					event.replyFailure("I could not find that trigger").queue();
					return;
				}

				List<Document> allActions = oldData.getList("actions", Document.class);
				if (allActions.size() >= TriggerCommand.MAX_ACTIONS) {
					event.replyFailure("You can only have a max of **" + TriggerCommand.MAX_ACTIONS + "** trigger actions").queue();
					return;
				}

				List<Document> actions = allActions.stream()
					.filter(d -> d.getInteger("type") == type.getId())
					.collect(Collectors.toList());

				if (actions.size() >= type.getMaxActions()) {
					event.replyFailure("You cannot have more than " + type.getMaxActions() + " `" + type + "` trigger action" + (type.getMaxActions() == 1 ? "" : "s")).queue();
					return;
				}

				event.replySuccess("That action has been added to that trigger").queue();
			});
		}

		@Command(value="set", description="Removes all of the trigger action type specified and adds this one to the trigger")
		@CommandId(0)
		@Examples({"trigger action set 600968f92850ef72c9af8756 request {\"url\": \"https://api.exchangerate.host/latest?base=EUR\", \"method\": \"POST\"}"})
		@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
		public void set(Sx4CommandEvent event, @Argument(value="id") ObjectId id, @Argument(value="type") TriggerActionType type, @Argument(value="json", endless=true) Document data) {
			Document action;
			try {
				action = TriggerUtility.parseTriggerAction(event, type, data);
			} catch (IllegalArgumentException e) {
				event.replyFailure(e.getMessage()).queue();
				return;
			}

			List<Bson> update = List.of(Operators.set("actions", Operators.concatArrays(Operators.filter("$actions", Operators.ne("$$this.type", type.getId())), List.of(action))));

			FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().projection(Projections.include("actions")).returnDocument(ReturnDocument.BEFORE);
			event.getMongo().findAndUpdateTrigger(Filters.and(Filters.eq("_id", id), Filters.eq("guildId", event.getGuild().getIdLong())), update, options).whenComplete((oldData, exception) -> {
				if (ExceptionUtility.sendExceptionally(event, exception)) {
					return;
				}

				if (oldData == null) {
					event.replyFailure("I could not find that trigger").queue();
					return;
				}

				event.replySuccess("That action has replaced all other actions of the same type on that trigger").queue();
			});
		}

		@Command(value="remove", description="Removes all of a specific action type from a trigger")
		@CommandId(487)
		@Examples({"trigger action remove 600968f92850ef72c9af8756 request"})
		@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
		public void remove(Sx4CommandEvent event, @Argument(value="id") ObjectId id, @Argument(value="type") TriggerActionType type) {
			List<Bson> update = List.of(Operators.set("actions", Operators.let(new Document("actions", Operators.filter("$actions", Operators.ne("$$this.type", type.getId()))), Operators.cond(Operators.isEmpty("$$actions"), "$actions", "$$actions"))));

			FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().projection(Projections.include("actions")).returnDocument(ReturnDocument.BEFORE);
			event.getMongo().findAndUpdateTrigger(Filters.and(Filters.eq("_id", id), Filters.eq("guildId", event.getGuild().getIdLong())), update, options).whenComplete((data, exception) -> {
				if (ExceptionUtility.sendExceptionally(event, exception)) {
					return;
				}

				if (data == null) {
					event.replyFailure("I could not find that trigger").queue();
					return;
				}

				List<Document> allActions = data.getList("actions", Document.class);
				List<Document> actions = allActions.stream()
					.filter(d -> d.getInteger("type") != type.getId())
					.collect(Collectors.toList());

				if (allActions.size() == actions.size()) {
					event.replyFailure("You do not have an action of that type on that trigger").queue();
					return;
				}

				if (actions.isEmpty()) {
					event.replyFailure("You have to have at least 1 action on a trigger").queue();
					return;
				}

				event.replySuccess("All actions of that type have been removed from that trigger").queue();
			});
		}

	}

}