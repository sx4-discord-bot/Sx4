package com.sx4.bot.commands.mod.auto;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.command.Command;
import com.mongodb.ErrorCategory;
import com.mongodb.MongoWriteException;
import com.mongodb.client.model.*;
import com.sx4.bot.annotations.argument.Limit;
import com.sx4.bot.annotations.command.AuthorPermissions;
import com.sx4.bot.annotations.command.CommandId;
import com.sx4.bot.annotations.command.Examples;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.database.mongo.model.Operators;
import com.sx4.bot.entities.argument.TimedArgument;
import com.sx4.bot.entities.management.WhitelistType;
import com.sx4.bot.entities.mod.action.ModAction;
import com.sx4.bot.entities.mod.auto.MatchAction;
import com.sx4.bot.entities.mod.auto.RegexType;
import com.sx4.bot.entities.settings.HolderType;
import com.sx4.bot.paged.PagedResult;
import com.sx4.bot.utility.ExceptionUtility;
import com.sx4.bot.utility.TimeUtility;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.regex.Pattern;

public class AntiRegexCommand extends Sx4Command {

	public AntiRegexCommand() {
		super("anti regex", 105);
		
		super.setAliases("antiregex");
		super.setDescription("Setup a regex which if matched will perform an action, use https://regex101.com/ for testing and select Java 8");
		super.setExamples("anti regex add", "anti regex remove", "anti regex list");
		super.setCategoryAll(ModuleCategory.AUTO_MODERATION);
	}
	
	public void onCommand(Sx4CommandEvent event) {
		event.replyHelp().queue();
	}
	
	@Command(value="add", description="Add a regex from `anti regex template list` to be checked on every message")
	@CommandId(106)
	@Examples({"anti regex add 5f023782ef9eba03390a740c"})
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	public void add(Sx4CommandEvent event, @Argument(value="id") ObjectId id) {
		Document regex = event.getMongo().getRegexTemplateById(id, Projections.include("pattern", "title", "type"));
		if (regex == null) {
			event.replyFailure("I could not find that regex template").queue();
			return;
		}

		List<Bson> guildPipeline = List.of(
			Aggregates.project(Projections.fields(Projections.computed("premium", Operators.lt(Operators.nowEpochSecond(), Operators.ifNull("$premium.endAt", 0L))), Projections.computed("guildId", "$_id"))),
			Aggregates.match(Filters.eq("guildId", event.getGuild().getIdLong()))
		);

		List<Bson> pipeline = List.of(
			Aggregates.match(Filters.and(Filters.eq("guildId", event.getGuild().getIdLong()), Filters.exists("enabled", false), Filters.eq("type", RegexType.REGEX.getId()))),
			Aggregates.group(null, Accumulators.sum("count", 1)),
			Aggregates.limit(10),
			Aggregates.unionWith("guilds", guildPipeline),
			Aggregates.group(null, Accumulators.max("count", "$count"), Accumulators.max("premium", "$premium")),
			Aggregates.project(Projections.fields(Projections.include("premium"), Projections.computed("count", Operators.ifNull("$count", 0))))
		);

		event.getMongo().aggregateRegexes(pipeline).thenCompose(iterable -> {
			Document counter = iterable.first();

			int count = counter == null ? 0 : counter.getInteger("count");
			if (count >= 3 && !counter.getBoolean("premium")) {
				throw new IllegalArgumentException("You need to have Sx4 premium to have more than 3 enabled anti regexes, you can get premium at <https://www.patreon.com/Sx4>");
			}

			if (count == 10) {
				throw new IllegalArgumentException("You cannot have any more than 10 anti regexes");
			}

			Document pattern = new Document("regexId", id)
				.append("guildId", event.getGuild().getIdLong())
				.append("type", regex.getInteger("type", RegexType.REGEX.getId()))
				.append("pattern", regex.getString("pattern"));

			return event.getMongo().insertRegex(pattern);
		}).thenCompose(result -> {
			event.replySuccess("The regex `" + result.getInsertedId().asObjectId().getValue().toHexString() + "` is now active").queue();

			return event.getMongo().updateRegexTemplateById(id, Updates.inc("uses", 1L));
		}).whenComplete((result, exception) -> {
			Throwable cause = exception instanceof CompletionException ? exception.getCause() : exception;
			if (cause instanceof MongoWriteException && ((MongoWriteException) cause).getError().getCategory() == ErrorCategory.DUPLICATE_KEY) {
				event.replyFailure("You already have that anti regex setup in this server").queue();
				return;
			} else if (cause instanceof IllegalArgumentException) {
				event.replyFailure(cause.getMessage()).queue();
				return;
			}

			ExceptionUtility.sendExceptionally(event, exception);
		});
	}

	@Command(value="add", description="Add a regex from `anti regex template list` to be checked on every message")
	@CommandId(125)
	@Examples({"anti regex add [0-9]+", "anti regex add https://discord\\.com/channels/([0-9]+)/([0-9]+)/?"})
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	public void add(Sx4CommandEvent event, @Argument(value="regex", endless=true) Pattern pattern) {
		List<Bson> guildPipeline = List.of(
			Aggregates.project(Projections.fields(Projections.computed("premium", Operators.lt(Operators.nowEpochSecond(), Operators.ifNull("$premium.endAt", 0L))), Projections.computed("guildId", "$_id"))),
			Aggregates.match(Filters.eq("guildId", event.getGuild().getIdLong()))
		);

		List<Bson> pipeline = List.of(
			Aggregates.match(Filters.and(Filters.eq("guildId", event.getGuild().getIdLong()), Filters.exists("enabled", false), Filters.eq("type", RegexType.REGEX.getId()))),
			Aggregates.group(null, Accumulators.sum("count", 1)),
			Aggregates.limit(10),
			Aggregates.unionWith("guilds", guildPipeline),
			Aggregates.group(null, Accumulators.max("count", "$count"), Accumulators.max("premium", "$premium")),
			Aggregates.project(Projections.fields(Projections.include("premium"), Projections.computed("count", Operators.ifNull("$count", 0))))
		);

		event.getMongo().aggregateRegexes(pipeline).thenCompose(iterable -> {
			Document counter = iterable.first();

			int count = counter == null ? 0 : counter.getInteger("count");
			if (count >= 3 && !counter.getBoolean("premium")) {
				throw new IllegalArgumentException("You need to have Sx4 premium to have more than 3 enabled anti regexes, you can get premium at <https://www.patreon.com/Sx4>");
			}

			if (count == 10) {
				throw new IllegalArgumentException("You cannot have any more than 10 anti regexes");
			}

			Document patternData = new Document("guildId", event.getGuild().getIdLong())
				.append("type", RegexType.REGEX.getId())
				.append("pattern", pattern.pattern());

			return event.getMongo().insertRegex(patternData);
		}).whenComplete((result, exception) -> {
			Throwable cause = exception instanceof CompletionException ? exception.getCause() : exception;
			if (cause instanceof MongoWriteException && ((MongoWriteException) cause).getError().getCategory() == ErrorCategory.DUPLICATE_KEY) {
				event.replyFailure("You already have that anti regex setup in this server").queue();
				return;
			} else if (cause instanceof IllegalArgumentException) {
				event.replyFailure(cause.getMessage()).queue();
				return;
			}

			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			event.replySuccess("The regex `" + result.getInsertedId().asObjectId().getValue().toHexString() + "` is now active").queue();
		});
	}

	@Command(value="toggle", aliases={"enable", "disable"}, description="Toggles the state of an anti regex")
	@CommandId(126)
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	@Examples({"anti regex toggle 5f023782ef9eba03390a740c"})
	public void toggle(Sx4CommandEvent event, @Argument(value="id") ObjectId id) {
		Bson filter = Filters.and(
			Filters.eq("guildId", event.getGuild().getIdLong()),
			Filters.exists("enabled", false),
			Filters.ne("type", RegexType.INVITE.getId())
		);

		List<Bson> guildPipeline = List.of(
			Aggregates.project(Projections.fields(Projections.computed("premium", Operators.lt(Operators.nowEpochSecond(), Operators.ifNull("$premium.endAt", 0L))), Projections.computed("guildId", "$_id"))),
			Aggregates.match(Filters.eq("guildId", event.getGuild().getIdLong()))
		);

		List<Bson> pipeline = List.of(
			Aggregates.match(Filters.and(Filters.eq("guildId", event.getGuild().getIdLong()), Filters.exists("enabled", false), Filters.eq("type", RegexType.REGEX.getId()))),
			Aggregates.project(Projections.include("_id")),
			Aggregates.group(null, Accumulators.push("regexes", Operators.ROOT)),
			Aggregates.unionWith("guilds", guildPipeline),
			Aggregates.group(null, Accumulators.max("regexes", "$regexes"), Accumulators.max("premium", "$premium")),
			Aggregates.project(Projections.fields(Projections.include("premium"), Projections.computed("count", Operators.size(Operators.ifNull("$regexes", Collections.EMPTY_LIST))), Projections.computed("disabled", Operators.isEmpty(Operators.filter(Operators.ifNull("$regexes", Collections.EMPTY_LIST), Operators.eq("$$this._id", id))))))
		);

		event.getMongo().aggregateRegexes(pipeline).thenCompose(iterable -> {
			Document data = iterable.first();

			boolean disabled = data == null || data.getBoolean("disabled");
			int count = data == null ? 0 : data.getInteger("count");
			if (data != null && disabled && count >= 3 && !data.getBoolean("premium")) {
				throw new IllegalArgumentException("You need to have Sx4 premium to have more than 3 enabled anti regexes, you can get premium at <https://www.patreon.com/Sx4>");
			}

			if (count >= 10) {
				throw new IllegalArgumentException("You can not have any more than 10 enabled anti regexes");
			}

			List<Bson> update = List.of(Operators.set("enabled", Operators.cond(Operators.exists("$enabled"), Operators.REMOVE, false)));
			FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER).projection(Projections.include("enabled"));

			return event.getMongo().findAndUpdateRegex(Filters.eq("_id", id), update, options);
		}).whenComplete((data, exception) -> {
			Throwable cause = exception instanceof CompletionException ? exception.getCause() : exception;
			if (cause instanceof IllegalArgumentException) {
				event.replyFailure(cause.getMessage()).queue();
				return;
			}

			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			event.replySuccess("The anti regex `" + id.toHexString() + "` is now **" + (data.get("enabled", true) ? "enabled" : "disabled") + "**").queue();
		});
	}

	@Command(value="remove", description="Removes a anti regex that you have setup")
	@CommandId(107)
	@Examples({"anti regex remove 5f023782ef9eba03390a740c"})
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	public void remove(Sx4CommandEvent event, @Argument(value="id") ObjectId id) {
		FindOneAndDeleteOptions options = new FindOneAndDeleteOptions().projection(Projections.include("regexId"));

		event.getMongo().findAndDeleteRegex(Filters.eq("_id", id), options).thenCompose(data -> {
			if (data == null) {
				event.replyFailure("You do that have that regex setup in this server").queue();
				return CompletableFuture.completedFuture(null);
			}

			if (data.containsKey("regexId")) {
				return event.getMongo().updateRegexTemplateById(id, Updates.inc("uses", -1L));
			} else {
				return CompletableFuture.completedFuture(null);
			}
		}).whenComplete((result, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			event.replySuccess("That regex is no longer active").queue();
		});
	}

	@Command(value="attempts", description="Sets the amount of attempts needed for the mod action to execute")
	@CommandId(108)
	@Examples({"anti regex attempts 5f023782ef9eba03390a740c 3", "anti regex attempts 5f023782ef9eba03390a740c 1"})
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	public void attempts(Sx4CommandEvent event, @Argument(value="id") ObjectId id, @Argument(value="attempts") @Limit(min=1) int attempts) {
		event.getMongo().updateRegex(Filters.eq("_id", id), Updates.set("attempts.amount", attempts)).whenComplete((result, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			if (result.getMatchedCount() == 0) {
				event.replyFailure("I could not find that anti regex").queue();
				return;
			}

			if (result.getModifiedCount() == 0) {
				event.replyFailure("Your attempts where already set to that").queue();
				return;
			}

			event.replySuccess("Attempts to a mod action have been set to **" + attempts + "**").queue();
		});
	}

	@Command(value="reset after", description="The time it should take for attempts to be taken away")
	@CommandId(109)
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	@Examples({"anti regex reset after 5f023782ef9eba03390a740c 1 1 day", "anti regex reset after 5f023782ef9eba03390a740c 3 5h 20s", "anti regex reset after 5f023782ef9eba03390a740c 3 5h 20s"})
	public void resetAfter(Sx4CommandEvent event, @Argument(value="id") ObjectId id, @Argument(value="amount") @Limit(min=0) int amount, @Argument(value="time", endless=true, nullDefault=true) Duration time) {
		if (time.toMinutes() < 5) {
			event.replyFailure("The duration has to be 5 minutes or above").queue();
			return;
		}

		Bson update = amount == 0 ? Updates.unset("attempts.reset") : Updates.set("attempts.reset", new Document("amount", amount).append("after", time.toSeconds()));
		event.getMongo().updateRegex(Filters.eq("_id", id), update).whenComplete((result, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			if (result.getMatchedCount() == 0) {
				event.replyFailure("I could not find that anti regex").queue();
				return;
			}

			if (result.getModifiedCount() == 0) {
				event.replyFailure("Your reset attempts configuration was already set to that").queue();
				return;
			}

			event.reply(amount == 0 ? "Users attempts will no longer reset" + event.getConfig().getSuccessEmote() : String.format("Users attempts will now reset **%d** time%s after `%s` %s", amount, amount == 1 ? "" : "s", TimeUtility.getTimeString(time.toSeconds()), event.getConfig().getSuccessEmote())).queue();
		});
	}

	@Command(value="list", description="Lists the regexes which are active in this server")
	@CommandId(110)
	@Examples({"anti regex list"})
	public void list(Sx4CommandEvent event) {
		List<Document> regexes = event.getMongo().getRegexes(Filters.eq("guildId", event.getGuild().getIdLong()), Projections.include("pattern")).into(new ArrayList<>());
		if (regexes.isEmpty()) {
			event.replyFailure("There are no regexes setup in this server").queue();
			return;
		}

		PagedResult<Document> paged = new PagedResult<>(event.getBot(), regexes)
			.setPerPage(6)
			.setCustomFunction(page -> {
				MessageBuilder builder = new MessageBuilder();

				EmbedBuilder embed = new EmbedBuilder();
				embed.setAuthor("Anti Regex", null, event.getGuild().getIconUrl());
				embed.setTitle("Page " + page.getPage() + "/" + page.getMaxPage());
				embed.setFooter(PagedResult.DEFAULT_FOOTER_TEXT, null);

				page.forEach((data, index) -> embed.addField(data.getObjectId("_id").toHexString(), "`" + data.getString("pattern") + "`", true));

				return builder.setEmbed(embed.build()).build();
			});

		paged.execute(event);
	}

	public static class ModCommand extends Sx4Command {

		public ModCommand() {
			super("mod", 111);

			super.setDescription("Set specific things to happen when someone reaches a certain amount of attempts");
			super.setExamples("anti regex mod message", "anti regex mod action");
		}

		public void onCommand(Sx4CommandEvent event) {
			event.replyHelp().queue();
		}

		@Command(value="message", description="Changes the message which is sent when someone hits the max attempts")
		@CommandId(231)
		@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
		@Examples({"anti regex mod message 5f023782ef9eba03390a740c A user has been banned for sending links", "anti regex match message 5f023782ef9eba03390a740c {user.name} has received a {regex.action}"})
		public void message(Sx4CommandEvent event, @Argument(value="id") ObjectId id, @Argument(value="message", endless=true) @Limit(max=1500) String message) {
			event.getMongo().updateRegex(Filters.eq("_id", id), Updates.set("mod.message", message)).whenComplete((result, exception) -> {
				if (ExceptionUtility.sendExceptionally(event, exception)) {
					return;
				}

				if (result.getMatchedCount() == 0) {
					event.replyFailure("I could not find that anti regex").queue();
					return;
				}

				if (result.getModifiedCount() == 0) {
					event.replyFailure("Your mod message for that regex was already set to that").queue();
					return;
				}

				event.replySuccess("Your mod message for that regex has been updated").queue();
			});
		}

		@Command(value="action", description="Sets the action to be taken when a user hits the max attempts")
		@CommandId(112)
		@Examples({"anti regex mod action 5f023782ef9eba03390a740c WARN", "anti regex mod action 5f023782ef9eba03390a740c MUTE 60m"})
		@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
		public void action(Sx4CommandEvent event, @Argument(value="id") ObjectId id, @Argument(value="action", endless=true) TimedArgument<ModAction> timedAction) {
			ModAction action = timedAction.getArgument();
			if (!action.isOffence()) {
				event.replyFailure("The action has to be an offence").queue();
				return;
			}

			Document modAction = new Document("type", action.getType());

			if (action.isTimed()) {
				Duration duration = timedAction.getDuration();
				if (duration == null) {
					event.replyFailure("You need to provide a duration for this mod action").queue();
					return;
				}

				modAction.append("duration", duration.toSeconds());
			}

			event.getMongo().updateRegex(Filters.eq("_id", id), Updates.set("mod.action", modAction)).whenComplete((result, exception) -> {
				if (ExceptionUtility.sendExceptionally(event, exception)) {
					return;
				}

				if (result.getMatchedCount() == 0) {
					event.replyFailure("I could not find that anti regex").queue();
					return;
				}

				if (result.getModifiedCount() == 0) {
					event.replyFailure("Your mod action for this regex is already set to that").queue();
					return;
				}

				event.replySuccess("Your mod action for that regex has been updated").queue();
			});
		}

	}

	public static class MatchCommand extends Sx4Command {

		public MatchCommand() {
			super("match", 113);

			super.setDescription("Set specific things to happen when a message is matched with a specific regex");
			super.setExamples("anti regex match action", "anti regex match message");
		}

		public void onCommand(Sx4CommandEvent event) {
			event.replyHelp().queue();
		}

		@Command(value="message", description="Changes the message which is sent when someone triggers an anti regex")
		@CommandId(114)
		@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
		@Examples({"anti regex match message 5f023782ef9eba03390a740c You cannot have a url in your message :no_entry:", "anti regex match message 5f023782ef9eba03390a740c {user.mention}, don't send that here or else you'll get a {regex.action} :no_entry:"})
		public void message(Sx4CommandEvent event, @Argument(value="id") ObjectId id, @Argument(value="message", endless=true) @Limit(max=1500) String message) {
			event.getMongo().updateRegex(Filters.eq("_id", id), Updates.set("match.message", message)).whenComplete((result, exception) -> {
				if (ExceptionUtility.sendExceptionally(event, exception)) {
					return;
				}

				if (result.getMatchedCount() == 0) {
					event.replyFailure("I could not find that anti regex").queue();
					return;
				}

				if (result.getModifiedCount() == 0) {
					event.replyFailure("Your match message for that regex was already set to that").queue();
					return;
				}

				event.replySuccess("Your match message for that regex has been updated").queue();
			});
		}

		@Command(value="action", description="Set what the bot should do when the regex is matched")
		@CommandId(115)
		@Examples({"anti regex match action 5f023782ef9eba03390a740c SEND_MESSAGE", "anti regex match action 5f023782ef9eba03390a740c SEND_MESSAGE DELETE_MESSAGE"})
		@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
		public void action(Sx4CommandEvent event, @Argument(value="id") ObjectId id, @Argument(value="actions") MatchAction... actions) {
			event.getMongo().updateRegex(Filters.eq("_id", id), Updates.set("match.action", MatchAction.getRaw(actions))).whenComplete((result, exception) -> {
				if (ExceptionUtility.sendExceptionally(event, exception)) {
					return;
				}

				if (result.getMatchedCount() == 0) {
					event.replyFailure("I could not find that anti regex").queue();
					return;
				}

				if (result.getModifiedCount() == 0) {
					event.replyFailure("Your match action for this regex is already set to that").queue();
					return;
				}

				event.replySuccess("Your match action for that regex has been updated").queue();
			});
		}

	}

	public static class WhitelistCommand extends Sx4Command {

		public WhitelistCommand() {
			super("whitelist", 116);

			super.setDescription("Whitelist roles and users from certain channels so they can ignore the anti regex");
			super.setExamples("anti regex whitelist add", "anti regex whitelist remove");
		}

		public void onCommand(Sx4CommandEvent event) {
			event.replyHelp().queue();
		}

		@Command(value="add", description="Adds a whitelist for a group in the regex")
		@CommandId(117)
		@Examples({"anti regex whitelist add 5f023782ef9eba03390a740c #youtube-links 2 youtube.com", "anti regex whitelist add 5f023782ef9eba03390a740c 0 https://youtube.com"})
		@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
		public void add(Sx4CommandEvent event, @Argument(value="id") ObjectId id, @Argument(value="channel", nullDefault=true) TextChannel channelArgument, @Argument(value="group") @Limit(min=0) int group, @Argument(value="string", endless=true) @Limit(max=250) String string) {
			Document regex = event.getMongo().getRegex(Filters.eq("_id", id), Projections.include("pattern", "type"));
			if (regex != null && Pattern.compile(regex.getString("pattern")).matcher("").groupCount() < group) {
				event.replyFailure("There is not a group " + group + " in that regex").queue();
				return;
			}

			List<TextChannel> channels = channelArgument == null ? event.getGuild().getTextChannels() : List.of(channelArgument);

			Bson channelMap = Operators.ifNull("$whitelist", Collections.EMPTY_LIST);

			Document groupData = new Document("group", group).append("strings", List.of(string));

			List<Bson> concat = new ArrayList<>();
			List<Long> channelIds = new ArrayList<>();
			for (TextChannel channel : channels) {
				long channelId = channel.getIdLong();
				channelIds.add(channelId);

				Document channelData = new Document("id", channelId).append("type", WhitelistType.CHANNEL.getId()).append("groups", List.of(groupData));

				Bson channelFilter = Operators.filter(channelMap, Operators.eq("$$this.id", channelId));
				Bson groupMap = Operators.ifNull(Operators.first(Operators.map(channelFilter, "$$this.groups")), Collections.EMPTY_LIST);
				Bson groupFilter = Operators.filter(groupMap, Operators.eq("$$this.group", group));

				concat.add(Operators.cond(Operators.isEmpty(channelFilter), List.of(channelData), Operators.cond(Operators.isEmpty(groupFilter), List.of(Operators.mergeObjects(Operators.first(channelFilter), new Document("groups", Operators.concatArrays(List.of(groupData), Operators.filter(groupMap, Operators.ne("$$this.group", group)))))), List.of(Operators.mergeObjects(Operators.first(channelFilter), new Document("groups", Operators.concatArrays(List.of(Operators.mergeObjects(Operators.first(groupFilter), new Document("strings", Operators.concatArrays(Operators.filter(Operators.first(Operators.map(groupFilter, "$$this.strings")), Operators.ne("$$this", string)), List.of(string))))), Operators.filter(groupMap, Operators.ne("$$this.group", group)))))))));
			}

			concat.add(Operators.filter(channelMap, Operators.not(Operators.in("$$this.id", channelIds))));
			List<Bson> update = List.of(Operators.set("whitelist", Operators.concatArrays(concat)));

			event.getMongo().updateRegex(Filters.eq("_id", id), update).whenComplete((result, exception) -> {
				if (ExceptionUtility.sendExceptionally(event, exception)) {
					return;
				}

				if (result.getMatchedCount() == 0) {
					event.replyFailure("I could not find that anti regex").queue();
					return;
				}


				if (result.getModifiedCount() == 0) {
					event.replyFailure("Group **" + group + "** is already whitelisted from that string in all of the provided channels").queue();
					return;
				}

				event.replySuccess("Group **" + group + "** is now whitelisted from that string in the provided channels").queue();
			});
		}

		@Command(value="add", description="Adds a whitelist for a role or user")
		@CommandId(118)
		@Examples({"anti regex whitelist add 5f023782ef9eba03390a740c #channel @everyone", "anti regex whitelist add 5f023782ef9eba03390a740c @Shea#6653"})
		@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
		public void add(Sx4CommandEvent event, @Argument(value="id") ObjectId id, @Argument(value="channel", nullDefault=true) TextChannel channelArgument, @Argument(value="user | role", endless=true) IPermissionHolder holder) {
			List<TextChannel> channels = channelArgument == null ? event.getGuild().getTextChannels() : List.of(channelArgument);

			Bson channelMap = Operators.ifNull("$whitelist", Collections.EMPTY_LIST);

			boolean role = holder instanceof Role;
			long holderId = holder.getIdLong();

			Document holderData = new Document("id", holderId).append("type", role ? HolderType.ROLE.getType() : HolderType.USER.getType());

			List<Bson> concat = new ArrayList<>();
			List<Long> channelIds = new ArrayList<>();
			for (TextChannel channel : channels) {
				long channelId = channel.getIdLong();
				channelIds.add(channelId);

				Document channelData = new Document("id", channelId).append("type", WhitelistType.CHANNEL.getId()).append("holders", List.of(holderData));

				Bson channelFilter = Operators.filter(channelMap, Operators.eq("$$this.id", channelId));
				concat.add(Operators.cond(Operators.isEmpty(channelFilter), List.of(channelData), List.of(Operators.mergeObjects(Operators.first(channelFilter), new Document("holders", Operators.concatArrays(List.of(holderData), Operators.filter(Operators.ifNull(Operators.first(Operators.map(channelFilter, "$$this.holders")), Collections.EMPTY_LIST), Operators.ne("$$this.id", holderId))))))));
			}

			concat.add(Operators.filter(channelMap, Operators.not(Operators.in("$$this.id", channelIds))));
			List<Bson> update = List.of(Operators.set("whitelist", Operators.concatArrays(concat)));
			event.getMongo().updateRegex(Filters.eq("_id", id), update).whenComplete((result, exception) -> {
				if (ExceptionUtility.sendExceptionally(event, exception)) {
					return;
				}

				if (result.getMatchedCount() == 0) {
					event.replyFailure("I could not find that anti regex").queue();
					return;
				}

				if (result.getModifiedCount() == 0) {
					event.replyFailure((role ? ((Role) holder).getAsMention() : ((Member) holder).getUser().getAsMention()) + " is already whitelisted in all of the provided channels").queue();
					return;
				}

				event.replySuccess((role ? ((Role) holder).getAsMention() : ((Member) holder).getUser().getAsMention()) + " is now whitelisted in the provided channels").queue();
			});
		}

		@Command(value="remove", description="Removes a group whitelist from channels")
		@CommandId(119)
		@Examples({"anti regex whitelist remove 5f023782ef9eba03390a740c #youtube-links 2 youtube.com", "anti regex whitelist remove 5f023782ef9eba03390a740c 0 https://youtube.com"})
		@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
		public void remove(Sx4CommandEvent event, @Argument(value="id") ObjectId id, @Argument(value="channel", nullDefault=true) TextChannel channelArgument, @Argument(value="group") @Limit(min=0) int group, @Argument(value="string", endless=true) String string) {
			List<TextChannel> channels = channelArgument == null ? event.getGuild().getTextChannels() : List.of(channelArgument);

			Bson channelMap = Operators.ifNull("$whitelist", Collections.EMPTY_LIST);

			List<Bson> concat = new ArrayList<>();
			List<Long> channelIds = new ArrayList<>();
			for (TextChannel channel : channels) {
				long channelId = channel.getIdLong();
				channelIds.add(channelId);

				Bson channelFilter = Operators.filter(channelMap, Operators.eq("$$this.id", channelId));
				Bson groupMap = Operators.ifNull(Operators.first(Operators.map(channelFilter, "$$this.groups")), Collections.EMPTY_LIST);
				Bson groupFilter = Operators.filter(groupMap, Operators.eq("$$this.group", group));

				concat.add(Operators.cond(Operators.or(Operators.isEmpty(channelFilter), Operators.isEmpty(groupFilter)), Collections.EMPTY_LIST, List.of(Operators.mergeObjects(Operators.first(channelFilter), new Document("groups", Operators.concatArrays(List.of(Operators.mergeObjects(Operators.first(groupFilter), new Document("strings", Operators.filter(Operators.first(Operators.map(groupFilter, "$$this.strings")), Operators.ne("$$this", string))))), Operators.filter(groupMap, Operators.ne("$$this.group", group))))))));
			}

			concat.add(Operators.filter(channelMap, Operators.not(Operators.in("$$this.id", channelIds))));
			List<Bson> update = List.of(Operators.set("whitelist", Operators.concatArrays(concat)));

			event.getMongo().updateRegex(Filters.eq("_id", id), update).whenComplete((result, exception) -> {
				if (ExceptionUtility.sendExceptionally(event, exception)) {
					return;
				}

				if (result.getMatchedCount() == 0) {
					event.replyFailure("I could not find that anti regex").queue();
					return;
				}

				if (result.getModifiedCount() == 0) {
					event.replyFailure("Group **" + group + "** is not whitelisted from that string in any of the provided channels").queue();
					return;
				}

				event.replySuccess("Group **" + group + "** is no longer whitelisted from that string in the provided channels").queue();
			});
		}

		@Command(value="remove", description="Removes a role or user whitelist from channels")
		@CommandId(120)
		@Examples({"anti regex whitelist remove 5f023782ef9eba03390a740c #channel @everyone", "anti regex whitelist remove 5f023782ef9eba03390a740c @Shea#6653"})
		@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
		public void remove(Sx4CommandEvent event, @Argument(value="id") ObjectId id, @Argument(value="channel", nullDefault=true) TextChannel channelArgument, @Argument(value="user | role") IPermissionHolder holder) {
			List<TextChannel> channels = channelArgument == null ? event.getGuild().getTextChannels() : List.of(channelArgument);

			Bson channelMap = Operators.ifNull("$whitelist", Collections.EMPTY_LIST);

			boolean role = holder instanceof Role;
			long holderId = holder.getIdLong();

			Document holderData = new Document("id", holderId).append("type", role ? HolderType.ROLE.getType() : HolderType.USER.getType());

			List<Bson> concat = new ArrayList<>();
			List<Long> channelIds = new ArrayList<>();
			for (TextChannel channel : channels) {
				long channelId = channel.getIdLong();
				channelIds.add(channelId);

				Document channelData = new Document("id", channelId).append("type", WhitelistType.CHANNEL.getId()).append("holders", List.of(holderData));

				Bson channelFilter = Operators.filter(channelMap, Operators.eq("$$this.id", channelId));
				concat.add(Operators.cond(Operators.isEmpty(channelFilter), List.of(channelData), List.of(Operators.mergeObjects(Operators.first(channelFilter), new Document("holders", Operators.filter(Operators.ifNull(Operators.first(Operators.map(channelFilter, "$$this.holders")), Collections.EMPTY_LIST), Operators.ne("$$this.id", holderId)))))));
			}

			concat.add(Operators.filter(channelMap, Operators.not(Operators.in("$$this.id", channelIds))));
			List<Bson> update = List.of(Operators.set("whitelist", Operators.concatArrays(concat)));

			event.getMongo().updateRegex(Filters.eq("_id", id), update).whenComplete((result, exception) -> {
				if (ExceptionUtility.sendExceptionally(event, exception)) {
					return;
				}

				if (result.getMatchedCount() == 0) {
					event.replyFailure("I could not find that anti regex").queue();
					return;
				}

				if (result.getModifiedCount() == 0) {
					event.replyFailure((role ? ((Role) holder).getAsMention() : ((Member) holder).getUser().getAsMention()) + " is not whitelisted in any of the provided channels").queue();
					return;
				}

				event.replySuccess((role ? ((Role) holder).getAsMention() : ((Member) holder).getUser().getAsMention()) + " is no longer whitelisted in the provided channels").queue();
			});
		}

		// TODO: Think of a good format
		@Command(value="list", description="Lists regex groups, roles and users that are whitelisted from specific channels for an anti regex")
		@CommandId(121)
		@Examples({"anti regex whitelist list 5f023782ef9eba03390a740c"})
		public void list(Sx4CommandEvent event, @Argument(value="id") ObjectId id, @Argument(value="channels") TextChannel channel) {
		    Document regex = event.getMongo().getRegex(Filters.eq("_id", id), Projections.include("whitelist"));
			if (regex == null) {
				event.replyFailure("I could not find that anti regex").queue();
				return;
			}

			List<Document> whitelists = regex.getList("whitelist", Document.class, Collections.emptyList());
			Document whitelist = whitelists.stream()
				.filter(d -> d.getLong("id") == channel.getIdLong())
				.findFirst()
				.orElse(null);

			if (whitelist == null) {
				event.replyFailure("Nothing is whitelisted in that channel").queue();
				return;
			}


		}

	}

	public static class TemplateCommand extends Sx4Command {

		public TemplateCommand() {
			super("template", 122);

			super.setDescription("Create regex templates for anti regex");
			super.setExamples("anti regex template add", "anti regex template list");
		}

		public void onCommand(Sx4CommandEvent event) {
			event.replyHelp().queue();
		}

		@Command(value="add", description="Add a regex to the templates for anyone to use")
		@CommandId(123)
		@Examples({"anti regex template add Numbers .*[0-9]+.* Will match any message which contains a number"})
		public void add(Sx4CommandEvent event, @Argument(value="title") String title, @Argument(value="regex") Pattern pattern, @Argument(value="description", endless=true) String description) {
			if (title.length() > 20) {
				event.replyFailure("The title cannot be more than 20 characters").queue();
				return;
			}

			if (description.length() > 250) {
				event.replyFailure("The description cannot be more than 250 characters").queue();
				return;
			}

			String patternString = pattern.pattern();
			if (patternString.length() > 200) {
				event.replyFailure("The regex cannot be more than 200 characters").queue();
				return;
			}

			Document data = new Document("title", title)
				.append("description", description)
				.append("pattern", patternString)
				.append("type", RegexType.REGEX.getId())
				.append("ownerId", event.getAuthor().getIdLong());

			event.getMongo().insertRegexTemplate(data).whenComplete((result, exception) -> {
				if (ExceptionUtility.sendExceptionally(event, exception)) {
					return;
				}

				event.replySuccess("Your regex has been added to the queue you will be notified when it has been approved or denied").queue();
			});
		}

		@Command(value="list", description="Lists the regexes which you can use for anti regex")
		@CommandId(124)
		@Examples({"anti regex template list"})
		public void list(Sx4CommandEvent event) {
			List<Document> list = event.getMongo().getRegexTemplates(Filters.empty(), Projections.include("title", "description", "pattern", "ownerId", "uses")).sort(Sorts.descending("uses")).into(new ArrayList<>());
			if (list.isEmpty()) {
				event.replyFailure("There are no regex templates currently").queue();
				return;
			}

			PagedResult<Document> paged = new PagedResult<>(event.getBot(), list)
				.setPerPage(6)
				.setCustomFunction(page -> {
					MessageBuilder builder = new MessageBuilder();

					EmbedBuilder embed = new EmbedBuilder();
					embed.setAuthor("Regex Template List", null, event.getSelfUser().getEffectiveAvatarUrl());
					embed.setTitle("Page " + page.getPage() + "/" + page.getMaxPage());
					embed.setFooter(PagedResult.DEFAULT_FOOTER_TEXT, null);

					page.forEach((data, index) -> {
						User owner = event.getShardManager().getUserById(data.getLong("ownerId"));

						embed.addField(data.getString("title"), String.format("Id: %s\nRegex: `%s`\nUses: %,d\nOwner: %s\nDescription: %s", data.getObjectId("_id").toHexString(), data.getString("pattern"), data.getLong("uses"), owner == null ? "Annonymous#0000" : owner.getAsTag(), data.getString("description")), true);
					});

					return builder.setEmbed(embed.build()).build();
				});

			paged.execute(event);
		}

	}
	
}
