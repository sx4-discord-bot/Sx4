package com.sx4.bot.commands.mod;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.command.Command;
import com.mongodb.client.model.*;
import com.sx4.bot.annotations.argument.Limit;
import com.sx4.bot.annotations.argument.Options;
import com.sx4.bot.annotations.command.*;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.database.mongo.MongoDatabase;
import com.sx4.bot.database.mongo.model.Operators;
import com.sx4.bot.entities.argument.Alternative;
import com.sx4.bot.entities.argument.TimedArgument;
import com.sx4.bot.entities.mod.Reason;
import com.sx4.bot.entities.mod.action.Action;
import com.sx4.bot.entities.mod.action.ModAction;
import com.sx4.bot.entities.mod.action.TimeAction;
import com.sx4.bot.entities.mod.action.Warn;
import com.sx4.bot.paged.PagedResult;
import com.sx4.bot.utility.ExceptionUtility;
import com.sx4.bot.utility.ModUtility;
import com.sx4.bot.utility.NumberUtility;
import com.sx4.bot.utility.TimeUtility;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.utils.MarkdownSanitizer;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class WarnCommand extends Sx4Command {

	public WarnCommand() {
		super("warn", 151);
		
		super.setAliases("warn user");
		super.setDescription("Warn a user in the server, warning can give punishments on each warn a user gets");
		super.setAuthorDiscordPermissions(Permission.MESSAGE_MANAGE);
		super.setExamples("warn @Shea#6653", "warn Shea Spamming", "warn Shea#6653 template:tos", "warn 402557516728369153 t:tos and Spamming");
		super.setCategoryAll(ModuleCategory.MODERATION);
	}
	
	public void onCommand(Sx4CommandEvent event, @Argument(value="user") Member member, @Argument(value="reason", endless=true, nullDefault=true) Reason reason) {
		if (member.getIdLong() == event.getSelfUser().getIdLong()) {
			event.replyFailure("You cannot warn me, that is illegal").queue();
			return;
		}
		
		if (member.canInteract(event.getMember())) {
			event.replyFailure("You cannot warn someone higher or equal than your top role").queue();
			return;
		}

		ModUtility.warn(event.getBot(), member, event.getMember(), reason).whenComplete((warning, exception) -> {
			if (exception != null) {
				event.replyFailure(exception.getMessage()).queue();
			} else {
				Warn warn = warning.getWarning();
				Action action = warn.getAction();

				event.replyFormat("**%s** has received a %s%s (%s warning) " + event.getConfig().getSuccessEmote(), member.getUser().getAsTag(), action.getModAction().getName().toLowerCase(), action instanceof TimeAction ? " for " + TimeUtility.getTimeString(((TimeAction) action).getDuration()) : "", NumberUtility.getSuffixed(warn.getNumber())).queue();
			}
		});
	}

	@Command(value="set", description="Set the amount of warns a user has")
	@CommandId(242)
	@Redirects({"set warns", "set warnings"})
	@Examples({"warn set @Shea#6653 2", "warn set Shea#6653 0", "warn set 402557516728369153 1"})
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	public void set(Sx4CommandEvent event, @Argument(value="user") Member member, @Argument(value="warnings") int warnings) {
		if (member.canInteract(event.getMember())) {
			event.replyFailure("You cannot change the amount of warnings of someone higher or equal than your top role").queue();
			return;
		}

		Document warnData = event.getMongo().getGuildById(event.getGuild().getIdLong(), Projections.include("warn")).get("warn", MongoDatabase.EMPTY_DOCUMENT);
		boolean punishments = warnData.get("punishments", true);

		int maxWarning = punishments ? warnData.getList("config", Document.class, Warn.DEFAULT_CONFIG)
			.stream()
			.map(d -> d.getInteger("number"))
			.max(Integer::compareTo)
			.get() : Integer.MAX_VALUE;

		if (warnings > maxWarning) {
			event.replyFailure("The max amount of warnings you can give is **" + maxWarning + "**").queue();
			return;
		}

		Bson update = warnings == 0 ? Updates.unset("warnings") : Updates.combine(Updates.set("warnings", warnings), Updates.set("lastWarning", Clock.systemUTC().instant().getEpochSecond()));
		Bson filter = Filters.and(
			Filters.eq("userId", member.getIdLong()),
			Filters.eq("guildId", event.getGuild().getIdLong())
		);

		event.getMongo().updateWarnings(filter, update, new UpdateOptions().upsert(true)).whenComplete((result, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			if (result.getModifiedCount() == 0 && result.getUpsertedId() == null) {
				event.replyFailure("That user already had that amount of warnings").queue();
				return;
			}

			event.replySuccess("That user now has **" + warnings + "** warning" + (warnings == 1 ? "" : "s")).queue();
		});
	}

	@Command(value="reset after", description="The time it should take for warns to be taken away")
	@CommandId(450)
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	@Examples({"warn reset after 1 1 day", "warn reset after 3 5h 20s", "warn reset after 3 30d"})
	public void resetAfter(Sx4CommandEvent event, @Argument(value="amount") @Limit(min=0) int amount, @Argument(value="time", endless=true, nullDefault=true) Duration time) {
		if (time != null && time.toMinutes() < 5) {
			event.replyFailure("The duration has to be 5 minutes or above").queue();
			return;
		}

		if (amount != 0 && time == null) {
			event.reply("You need to provide a duration if attempts is more than 0").queue();
			return;
		}

		Bson update = amount == 0 ? Updates.unset("warn.reset") : Updates.set("warn.reset", new Document("amount", amount).append("after", time.toSeconds()));
		event.getMongo().updateGuildById(event.getGuild().getIdLong(), update).whenComplete((result, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			if (result.getModifiedCount() == 0) {
				event.replyFailure("Your warn reset configuration was already set to that").queue();
				return;
			}

			event.reply(amount == 0 ? "Users warns will no longer reset" + event.getConfig().getSuccessEmote() : String.format("Users warns will now reset **%d** time%s after `%s` %s", amount, amount == 1 ? "" : "s", TimeUtility.getTimeString(time.toSeconds()), event.getConfig().getSuccessEmote())).queue();
		});
	}

	@Command(value="list", description="Lists all the warned users in the server and how many warnings they have")
	@CommandId(258)
	@Examples({"warn list"})
	@BotPermissions(permissions={Permission.MESSAGE_EMBED_LINKS})
	public void list(Sx4CommandEvent event) {
		List<Bson> pipeline = List.of(
			Aggregates.match(Filters.eq("guildId", event.getGuild().getIdLong())),
			Aggregates.project(Projections.fields(Projections.include("userId"), Projections.computed("warnings", Operators.cond(Operators.or(Operators.isNull("$reset"), Operators.isNull("$warnings")), Operators.ifNull("$warnings", 0), Operators.max(0, Operators.subtract("$warnings", Operators.multiply(Operators.toInt(Operators.floor(Operators.divide(Operators.subtract(Operators.nowEpochSecond(), "$lastWarning"), "$reset.after"))), "$reset.amount"))))))),
			Aggregates.match(Filters.ne("warnings", 0)),
			Aggregates.sort(Sorts.descending("warnings"))
		);

		event.getMongo().aggregateWarnings(pipeline).whenComplete((users, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			if (users.isEmpty()) {
				event.replyFailure("There are no users with warnings in this server").queue();
				return;
			}

			PagedResult<Document> paged = new PagedResult<>(event.getBot(), users)
				.setAuthor("Warned Users", null, event.getGuild().getIconUrl())
				.setIndexed(false)
				.setDisplayFunction(data -> {
					long userId = data.getLong("userId");
					User user = event.getShardManager().getUserById(userId);

					return "`" + (user == null ? "Anonymous#0000 (" + userId + ")" : MarkdownSanitizer.escape(user.getAsTag())) + "` - Warning **#" + data.getInteger("warnings") + "**";
				});

			paged.execute(event);
		});
	}

	@Command(value="view", description="View the amount of warnings a specific user is on")
	@CommandId(259)
	@Examples({"warn view @Shea#6653", "warn view Shea", "warn view 402557516728369153"})
	@Redirects({"warnings", "warns"})
	public void view(Sx4CommandEvent event, @Argument(value="user", endless=true) Member member) {
		List<Bson> pipeline = List.of(
			Aggregates.match(Filters.and(Filters.eq("userId", member.getIdLong()), Filters.eq("guildId", event.getGuild().getIdLong()))),
			Aggregates.project(Projections.computed("warnings", Operators.cond(Operators.or(Operators.isNull("$reset"), Operators.isNull("$warnings")), Operators.ifNull("$warnings", 0), Operators.max(0, Operators.subtract("$warnings", Operators.multiply(Operators.toInt(Operators.floor(Operators.divide(Operators.subtract(Operators.nowEpochSecond(), "$lastWarning"), "$reset.after"))), "$reset.amount"))))))
		);

		event.getMongo().aggregateWarnings(pipeline).whenComplete((documents, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			Document data = documents.isEmpty() ? MongoDatabase.EMPTY_DOCUMENT : documents.get(0);
			int warnings = data.getInteger("warnings", 0);

			event.reply("**" + member.getUser().getAsTag() + "** is currently on **" + warnings + "** warning" + (warnings == 1 ? "" : "s")).queue();
		});
	}

	public static class ConfigCommand extends Sx4Command {

		public ConfigCommand() {
			super("configuration", 236);

			super.setDescription("Set the configuration for the warn system in the current server");
			super.setAliases("config");
			super.setExamples("warn configuration set", "warn configuration remove", "warn configuration punishments");
			super.setCategoryAll(ModuleCategory.MODERATION);
		}

		public void onCommand(Sx4CommandEvent event) {
			event.replyHelp().queue();
		}

		@Command(value="set", description="Set the action to occur when a user reaches a certain amount of warnings")
		@CommandId(237)
		@Examples({"warn configuration set 5 ban", "warn configuration set 2 mute 1h", "warn configuration set 4 temporary_ban 7d"})
		@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
		public void set(Sx4CommandEvent event, @Argument(value="warnings") @Limit(min=1, max=50) int warnings, @Argument(value="action", endless=true) TimedArgument<ModAction> timedAction) {
			ModAction action = timedAction.getArgument();
			if (!action.isOffence()) {
				event.replyFailure("The action has to be an offence").queue();
				return;
			}

			if (action == ModAction.WARN) {
				event.replyFailure("You cannot set a warning to warn").queue();
				return;
			}

			Document modAction = new Document("type", action.getType());

			Duration duration;
			if (action.isTimed()) {
				duration = timedAction.getDuration();
				if (duration == null) {
					event.replyFailure("You need to provide a duration for this mod action").queue();
					return;
				}

				modAction.append("duration", duration.toSeconds());
			} else {
				duration = null;
			}

			Document warn = new Document("action", modAction).append("number", warnings);

			List<Bson> update = List.of(Operators.set("warn.config", Operators.concatArrays(Operators.filter(Operators.ifNull("$warn.config", Warn.DEFAULT_CONFIG), Operators.ne("$$this.number", warnings)), List.of(warn))));
			FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().projection(Projections.include("warn.config")).upsert(true).returnDocument(ReturnDocument.BEFORE);

			event.getMongo().findAndUpdateGuildById(event.getGuild().getIdLong(), update, options).whenComplete((data, exception) -> {
				if (ExceptionUtility.sendExceptionally(event, exception)) {
					return;
				}

				data = data == null ? MongoDatabase.EMPTY_DOCUMENT : data;

				List<Document> config = data.getEmbedded(List.of("warn", "config"), Warn.DEFAULT_CONFIG);
				Document oldAction = config.stream()
					.filter(d -> d.getInteger("number") == warnings)
					.map(d -> d.get("action", Document.class))
					.findFirst()
					.orElse(null);

				if (oldAction != null && oldAction.getInteger("type") == action.getType() && oldAction.get("duration", 0L) == (duration == null ? 0L : duration.toSeconds())) {
					event.replyFailure("Warning #" + warnings + " already had that configuration").queue();
					return;
				}

				event.replySuccess("Warning #" + warnings + " will now give the user a " + action.getName().toLowerCase() + (duration == null ? "" : " for " + TimeUtility.getTimeString(duration.toSeconds()))).queue();
			});
		}

		@Command(value="remove", description="Removes an action from being taken when a user reaches a certain amount of warning")
		@CommandId(238)
		@Examples({"warn configuration remove 3", "warn configuration remove 1", "warn configuration remove all"})
		@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
		public void remove(Sx4CommandEvent event, @Argument(value="warnings | all") @Options("all") Alternative<Integer> option) {
			Integer warnings = option.getValue();

			List<Bson> update;
			if (option.isAlternative()) {
				update = List.of(Operators.unset("warn.config"));
			} else {
				Bson warnConfig = Operators.ifNull("$warn.config", Warn.DEFAULT_CONFIG);
				update = List.of(Operators.set("warn.config", Operators.cond(Operators.eq(Operators.size(warnConfig), 1), "$warn.config", Operators.filter(warnConfig, Operators.ne("$$this.number", warnings)))));
			}

			FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().projection(Projections.include("warn.config")).returnDocument(ReturnDocument.BEFORE);

			event.getMongo().findAndUpdateGuildById(event.getGuild().getIdLong(), update, options).whenComplete((data, exception) -> {
				if (ExceptionUtility.sendExceptionally(event, exception)) {
					return;
				}

				if (option.isAlternative()) {
					event.replySuccess("Your warn configuration has been reset to the default").queue();
					return;
				}

				data = data == null ? MongoDatabase.EMPTY_DOCUMENT : data;

				List<Document> config = data.getEmbedded(List.of("warn", "config"), Warn.DEFAULT_CONFIG);
				if (config.size() == 1) {
					event.replyFailure("You cannot have less than 1 action, use `all` as an argument to go back to the default configuration").queue();
					return;
				}

				Document oldAction = config.stream()
					.filter(d -> d.getInteger("number").equals(warnings))
					.map(d -> d.get("action", Document.class))
					.findFirst()
					.orElse(null);

				if (oldAction == null) {
					event.replyFailure("There was no configuration for warning #" + warnings).queue();
					return;
				}

				event.replySuccess("Warning #" + warnings + " will no longer have an action").queue();
			});
		}

		@Command(value="list", description="Lists the servers warn configuration")
		@CommandId(239)
		@Examples({"warn configuration list"})
		public void list(Sx4CommandEvent event) {
			List<Document> config = event.getMongo().getGuildById(event.getGuild().getIdLong(), Projections.include("warn.config")).getEmbedded(List.of("warn", "config"), new ArrayList<>(Warn.DEFAULT_CONFIG));
			config.sort(Comparator.comparingInt(a -> a.getInteger("number")));

			PagedResult<Document> paged = new PagedResult<>(event.getBot(), config)
				.setAuthor("Warn Configuration", null, event.getGuild().getIconUrl())
				.setIndexed(false)
				.setDisplayFunction(d -> "Warning #" + d.getInteger("number") + ": " + Action.fromData(d.get("action", Document.class)).toString());

			paged.execute(event);
		}

		@Command(value="punishments", aliases={"punish"}, description="Toggle the state of whether warns should give punishments per action")
		@CommandId(240)
		@Examples({"warn configuration punishments"})
		@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
		public void punishments(Sx4CommandEvent event) {
			List<Bson> update = List.of(Operators.set("warn.punishments", Operators.cond(Operators.exists("$warn.punishments"), Operators.REMOVE, false)));
			event.getMongo().findAndUpdateGuildById(event.getGuild().getIdLong(), Projections.include("warn.punishments"), update).whenComplete((data, exception) -> {
				if (ExceptionUtility.sendExceptionally(event, exception)) {
					return;
				}

				event.replySuccess("Punishments for warns are now **" + (data.getEmbedded(List.of("warn", "punishments"), true) ? "enabled" : "disabled") + "**").queue();
			});
		}

	}

}
