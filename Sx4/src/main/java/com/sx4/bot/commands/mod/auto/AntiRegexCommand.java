package com.sx4.bot.commands.mod.auto;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.command.Command;
import com.jockie.bot.core.command.Command.Developer;
import com.mongodb.MongoWriteException;
import com.mongodb.client.model.*;
import com.sx4.bot.annotations.argument.Limit;
import com.sx4.bot.annotations.command.AuthorPermissions;
import com.sx4.bot.annotations.command.CommandId;
import com.sx4.bot.annotations.command.Examples;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.database.Database;
import com.sx4.bot.database.model.Operators;
import com.sx4.bot.entities.argument.TimedArgument;
import com.sx4.bot.entities.mod.action.ModAction;
import com.sx4.bot.entities.mod.auto.MatchAction;
import com.sx4.bot.entities.settings.HolderType;
import com.sx4.bot.managers.AntiRegexManager;
import com.sx4.bot.paged.PagedResult;
import com.sx4.bot.utility.ExceptionUtility;
import com.sx4.bot.utility.TimeUtility;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;
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
		super.setDescription("Setup a regex which if matched with the content of a message it will perform an action");
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
		Document regex = this.database.getRegexById(id, Projections.include("approved", "pattern", "title"));
		if (!regex.getBoolean("approved", false)) {
			event.replyFailure("I could not find that regex template").queue();
			return;
		}
		
		Document pattern = new Document("id", id)
			.append("pattern", regex.getString("pattern"));
		
		List<Bson> update = List.of(Operators.set("antiRegex.regexes", Operators.cond(Operators.or(Operators.and(Operators.extinct("$premium"), Operators.gte(Operators.ifNull(Operators.size("$antiRegex.regexes"), 0), 10)), Operators.and(Operators.exists("$antiRegex.regexes"), Operators.not(Operators.isEmpty(Operators.filter("$antiRegex.regexes", Operators.eq("$$this.id", id)))))), "$antiRegex.regexes", Operators.concatArrays(Operators.ifNull("$antiRegex.regexes", Collections.EMPTY_LIST), List.of(pattern)))));

		FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().returnDocument(ReturnDocument.BEFORE).projection(Projections.include("premium", "antiRegex.regexes"));
		this.database.findAndUpdateGuildById(event.getGuild().getIdLong(), update, options)
			.thenCompose(data -> {
				data = data == null ? Database.EMPTY_DOCUMENT : data;
				List<Document> regexes = data.getEmbedded(List.of("antiRegex", "regexes"), Collections.emptyList());
				if (regexes.stream().anyMatch(d -> d.getObjectId("id").equals(id))) {
					event.replyFailure("You already have that regex setup in this server").queue();
					return CompletableFuture.completedFuture(null);
				}

				if (regexes.size() >= 10 && !data.containsKey("premium")) {
					event.replyFailure("You need to have Sx4 premium to have more than 10 anti regexes, you can get premium at <https://www.patreon.com/Sx4>").queue();
					return CompletableFuture.completedFuture(null);
				}

				return this.database.updateRegexById(id, Updates.addToSet("uses", event.getGuild().getIdLong()));
			}).whenComplete((result, exception) -> {
				if (ExceptionUtility.sendExceptionally(event, exception) || result == null) {
					return;
				}

				event.replySuccess("The regex **" + regex.getString("title") + "** is now active").queue();
			});
	}

	@Command(value="remove", description="Removes a anti regex that you have setup")
	@CommandId(107)
	@Examples({"anti regex remove 5f023782ef9eba03390a740c"})
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	public void remove(Sx4CommandEvent event, @Argument(value="id") ObjectId id) {
		this.database.updateGuildById(event.getGuild().getIdLong(), Updates.pull("antiRegex.regexes", Filters.eq("id", id)))
			.thenCompose(result -> {
				if (result.getModifiedCount() == 0) {
					event.replyFailure("You do that have that regex setup in this server").queue();
					return CompletableFuture.completedFuture(null);
				}

				return this.database.updateRegexById(id, Updates.pull("uses", event.getGuild().getIdLong()));
			}).whenComplete((result, exception) -> {
				if (ExceptionUtility.sendExceptionally(event, exception) || result == null) {
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
		Bson filter = Operators.filter("$antiRegex.regexes", Operators.eq("$$this.id", id));
		Bson modActionMap = Operators.first(Operators.map(filter, "$$this.action.mod"));
		List<Bson> update = List.of(Operators.set("antiRegex.regexes", Operators.cond(Operators.or(Operators.extinct("$antiRegex.regexes"), Operators.isEmpty(filter), Operators.isNull(modActionMap)), "$antiRegex.regexes", Operators.concatArrays(List.of(Operators.mergeObjects(Operators.first(filter), new Document("action", Operators.mergeObjects(Operators.ifNull(Operators.first(Operators.map(filter, "$$this.action")), Database.EMPTY_DOCUMENT), new Document("mod", Operators.mergeObjects(modActionMap, new Document("attempts", Operators.mergeObjects(Operators.first(Operators.map(filter, "$$this.action.mod.attempts")), new Document("amount", attempts))))))))), Operators.filter("$antiRegex.regexes", Operators.ne("$$this.id", id))))));

		FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().returnDocument(ReturnDocument.BEFORE).projection(Projections.include("antiRegex.regexes"));
		this.database.findAndUpdateGuildById(event.getGuild().getIdLong(), update, options).whenComplete((data, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			data = data == null ? Database.EMPTY_DOCUMENT : data;

			List<Document> regexes = data.getEmbedded(List.of("antiRegex", "regexes"), Collections.emptyList());
			Document regex = regexes.stream()
				.filter(d -> d.getObjectId("id").equals(id))
				.findFirst()
				.orElse(null);

			if (regex == null) {
				event.replyFailure("I could not find that regex").queue();
				return;
			}

			Document modActionData = regex.getEmbedded(List.of("action", "mod"), Document.class);
			if (modActionData == null) {
				event.replyFailure("You need a mod action to be set up to change the attempts").queue();
				return;
			}

			if (modActionData.getEmbedded(List.of("attempts", "amount"), 3) == attempts) {
				event.replyFailure("Your attempts are already set to **" + attempts + "**").queue();
				return;
			}

			event.replySuccess("Attempts to a mod action have been set to **" + attempts + "**").queue();
		});
	}

	@Command(value="reset after", description="The time it should take for an attempt(s) to be taken away")
	@CommandId(109)
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	@Examples({"anti regex reset after 5f023782ef9eba03390a740c 1 1 day", "anti regex reset after 5f023782ef9eba03390a740c 3 5h 20s", "anti regex reset after 5f023782ef9eba03390a740c 3 5h 20s"})
	public void resetAfter(Sx4CommandEvent event, @Argument(value="id") ObjectId id, @Argument(value="amount") @Limit(min=0) int amount, @Argument(value="time", endless=true, nullDefault=true) Duration time) {
		if (time.toMinutes() < 5) {
			event.replyFailure("The duration has to be 5 minutes or above").queue();
			return;
		}

		Bson update = amount == 0 ? Updates.unset("antiRegex.regexes.$[regex].action.mod.attempts.reset") : Updates.set("antiRegex.regexes.$[regex].action.mod.attempts.reset", new Document("amount", amount).append("after", time.toSeconds()));

		UpdateOptions options = new UpdateOptions().arrayFilters(List.of(Filters.eq("regex.id", id)));
		this.database.updateGuildById(event.getGuild().getIdLong(), update, options).whenComplete((result, exception) -> {
			if (exception instanceof CompletionException) {
				Throwable cause = exception.getCause();
				if (cause instanceof MongoWriteException && ((MongoWriteException) cause).getCode() == 2) {
					event.replyFailure("I could not find that regex").queue();
					return;
				}
			}

			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			if (result.getModifiedCount() == 0) {
				event.replyFailure("Your reset attempts configuration was already set to that").queue();
				return;
			}

			String message = amount == 0 ?
				"Users attempts will no longer reset" + this.config.getSuccessEmote() :
				String.format("Users attempts will now reset **%d** time%s after `%s` %s", amount, amount == 1 ? "" : "s", TimeUtility.getTimeString(time.toSeconds()), this.config.getSuccessEmote());

			event.reply(message).queue();
		});
	}

	@Command(value="list", description="Lists the regexes which are active in this server")
	@CommandId(110)
	@Examples({"anti regex list"})
	public void list(Sx4CommandEvent event) {
		List<Document> regexes = this.database.getGuildById(event.getGuild().getIdLong(), Projections.include("antiRegex.regexes")).getEmbedded(List.of("antiRegex", "regexes"), Collections.emptyList());
		if (regexes.isEmpty()) {
			event.replyFailure("There are no regexes setup in this server").queue();
			return;
		}

		PagedResult<Document> paged = new PagedResult<>(regexes)
			.setPerPage(6)
			.setCustomFunction(page -> {
				MessageBuilder builder = new MessageBuilder();

				EmbedBuilder embed = new EmbedBuilder();
				embed.setAuthor("Anti Regex", null, event.getGuild().getIconUrl());
				embed.setTitle("Page " + page.getPage() + "/" + page.getMaxPage());
				embed.setFooter("next | previous | go to <page_number> | cancel", null);

				page.forEach((data, index) -> embed.addField(data.getObjectId("id").toHexString(), "`" + data.getString("pattern") + "`", true));

				return builder.setEmbed(embed.build()).build();
			});

		paged.execute(event);
	}

	public class ModCommand extends Sx4Command {

		public ModCommand() {
			super("mod", 111);

			super.setDescription("Set specific things to happen when someone reaches a certain amount of attempts");
			super.setExamples("anti regex mod message", "anti regex mod action");
		}

		// TODO
		@Command(value="action", description="Sets the action to be taken when a user hits the max attempts")
		@CommandId(112)
		@Examples({"anti regex mod action 5f023782ef9eba03390a740c WARN", "anti regex mod action 5f023782ef9eba03390a740c MUTE 60m"})
		@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
		public void action(Sx4CommandEvent event, @Argument(value="id") ObjectId id /*, @Argument(value="action", endless=true) TimedArgument<ModAction> timedAction*/) {
			TimedArgument<ModAction> timedAction = new TimedArgument<>(null, ModAction.WARN);
			ModAction action = timedAction.getArgument();
			if (!action.isOffence()) {
				event.replyFailure("The action has to be an offence").queue();
				return;
			}

			Document modAction = new Document("type", action.getType());

			long duration;
			if (timedAction.hasDuration() && (action == ModAction.MUTE || action == ModAction.MUTE_EXTEND)) {
				duration = timedAction.getDuration().toSeconds();
				modAction.append("duration", duration);
			} else {
				duration = 0L;
			}

			Bson filter = Operators.filter("$antiRegex.regexes", Operators.eq("$$this.id", id));
			Bson modMap = Operators.first(Operators.map(filter, "$$this.action.mod"));
			List<Bson> update = List.of(Operators.set("antiRegex.regexes", Operators.cond(Operators.or(Operators.extinct("$antiRegex.regexes"), Operators.isEmpty(filter)), "$antiRegex.regexes", Operators.concatArrays(List.of(Operators.mergeObjects(Operators.first(filter), new Document("action", Operators.mergeObjects(Operators.ifNull(Operators.first(Operators.map(filter, "$$this.action")), Database.EMPTY_DOCUMENT), new Document("mod", Operators.mergeObjects(modMap, modAction)))))), Operators.filter("$antiRegex.regexes", Operators.ne("$$this.id", id))))));

			FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().returnDocument(ReturnDocument.BEFORE);
			this.database.findAndUpdateGuildById(event.getGuild().getIdLong(), update, options).whenComplete((data, exception) -> {
				if (ExceptionUtility.sendExceptionally(event, exception)) {
					return;
				}

				data = data == null ? Database.EMPTY_DOCUMENT : data;

				List<Document> regexes = data.getEmbedded(List.of("antiRegex", "regexes"), Collections.emptyList());
				Document regex = regexes.stream()
					.filter(d -> d.getObjectId("id").equals(id))
					.findFirst()
					.orElse(null);

				if (regex == null) {
					event.replyFailure("I could not find that regex").queue();
					return;
				}

				Document modActionData = regex.getEmbedded(List.of("action", "mod"), Document.class);
				if (modActionData != null && modActionData.getInteger("type") == action.getType() && modActionData.get("duration", 0L) == duration) {
					event.replyFailure("Your mod action for this regex is already set to that").queue();
					return;
				}

				event.replySuccess("Your mod action for that regex has been updated").queue();
			});
		}

	}

	public class MatchCommand extends Sx4Command {

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
		public void message(Sx4CommandEvent event, @Argument(value="id") ObjectId id, @Argument(value="message", endless=true) String message) {
			if (message.length() > 1500) {
				event.reply("Your message cannot be longer than 1500 characters :no_entry:").queue();
				return;
			}

			Bson filter = Operators.filter("$antiRegex.regexes", Operators.eq("$$this.id", id));
			List<Bson> update = List.of(Operators.set("antiRegex.regexes", Operators.cond(Operators.or(Operators.extinct("$antiRegex.regexes"), Operators.isEmpty(filter)), "$antiRegex.regexes", Operators.concatArrays(Operators.filter("$antiRegex.regexes", Operators.ne("$$this.id", id)), List.of(Operators.mergeObjects(Operators.first(filter), new Document("action", Operators.mergeObjects(Operators.ifNull(Operators.first(Operators.map(filter, "$$this.action")), Database.EMPTY_DOCUMENT), new Document("match", Operators.mergeObjects(Operators.ifNull(Operators.first(Operators.map(filter, "$$this.action.match")), Database.EMPTY_DOCUMENT), new Document("message", message)))))))))));

			FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().returnDocument(ReturnDocument.BEFORE);
			this.database.findAndUpdateGuildById(event.getGuild().getIdLong(), update, options).whenComplete((data, exception) -> {
				if (ExceptionUtility.sendExceptionally(event, exception)) {
					return;
				}

				data = data == null ? Database.EMPTY_DOCUMENT : data;

				List<Document> regexes = data.getEmbedded(List.of("antiRegex", "regexes"), Collections.emptyList());
				Document regex = regexes.stream()
					.filter(d -> d.getObjectId("id").equals(id))
					.findFirst()
					.orElse(null);

				if (regex == null) {
					event.replyFailure("I could not find that regex").queue();
					return;
				}

				String oldMessage = regex.get("message", AntiRegexManager.DEFAULT_MATCH_MESSAGE);
				if (oldMessage.equals(message)) {
					event.replyFailure("Your message for that regex was already set to that").queue();
					return;
				}

				event.replySuccess("Your message for that regex has been updated").queue();
			});
		}

		@Command(value="action", description="Set what the bot should do when the regex is matched")
		@CommandId(115)
		@Examples({"anti regex match action 5f023782ef9eba03390a740c SEND_MESSAGE", "anti regex match action 5f023782ef9eba03390a740c SEND_MESSAGE DELETE_MESSAGE"})
		@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
		public void action(Sx4CommandEvent event, @Argument(value="id") ObjectId id, @Argument(value="actions") MatchAction... actions) {
			long raw = MatchAction.getRaw(actions);

			Bson filter = Operators.filter("$antiRegex.regexes", Operators.eq("$$this.id", id));
			List<Bson> update = List.of(Operators.set("antiRegex.regexes", Operators.cond(Operators.or(Operators.extinct("$antiRegex.regexes"), Operators.isEmpty(filter)), "$antiRegex.regexes", Operators.concatArrays(List.of(Operators.mergeObjects(Operators.first(filter), new Document("action", Operators.mergeObjects(Operators.ifNull(Operators.first(Operators.map(filter, "$$this.action")), Database.EMPTY_DOCUMENT), new Document("match", Operators.mergeObjects(Operators.first(Operators.map(filter, "$$this.action.match")), new Document("action", raw))))))), Operators.filter("$antiRegex.regexes", Operators.ne("$$this.id", id))))));

			FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().returnDocument(ReturnDocument.BEFORE);
			this.database.findAndUpdateGuildById(event.getGuild().getIdLong(), update, options).whenComplete((data, exception) -> {
				if (ExceptionUtility.sendExceptionally(event, exception)) {
					return;
				}

				System.out.println("hi");

				data = data == null ? Database.EMPTY_DOCUMENT : data;

				List<Document> regexes = data.getEmbedded(List.of("antiRegex", "regexes"), Collections.emptyList());
				Document regex = regexes.stream()
					.filter(d -> d.getObjectId("id").equals(id))
					.findFirst()
					.orElse(null);

				if (regex == null) {
					event.replyFailure("I could not find that regex").queue();
					return;
				}

				long matchActionRaw = regex.getEmbedded(List.of("action", "match", "raw"), MatchAction.ALL);
				if (matchActionRaw == raw) {
					event.replyFailure("Your match action for this regex is already set to that").queue();
					return;
				}

				event.replySuccess("Your match action for that regex has been updated").queue();
			});
		}

	}

	public class WhitelistCommand extends Sx4Command {

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
		public void add(Sx4CommandEvent event, @Argument(value="id") ObjectId id, @Argument(value="channel", nullDefault=true) TextChannel channelArgument, @Argument(value="group") @Limit(min=0) int group, @Argument(value="string", endless=true) String string) {
			List<Document> patterns = this.database.getGuildById(event.getGuild().getIdLong(), Projections.include("antiRegex.regexes")).getEmbedded(List.of("antiRegex", "regexes"), Collections.emptyList());
			Document pattern = patterns.stream()
				.filter(d -> d.getObjectId("id").equals(id))
				.findFirst()
				.orElse(null);
			
			if (pattern == null) {
				event.replyFailure("I could not find that anti regex").queue();
				return;
			}
			
			if (Pattern.compile(pattern.getString("pattern")).matcher("").groupCount() < group) {
				event.replyFailure("There is not a group " + group + " in that regex").queue();
				return;
			}
			
			List<TextChannel> channels = channelArgument == null ? event.getGuild().getTextChannels() : List.of(channelArgument);

			Bson regexFilter = Operators.filter("$antiRegex.regexes", Operators.eq("$$this.id", id));
			Bson channelMap = Operators.ifNull(Operators.first(Operators.map(regexFilter, "$$this.whitelist.channels")), Collections.EMPTY_LIST);

			Document groupData = new Document("group", group).append("strings", List.of(string));
			List<Bson> concat = new ArrayList<>(), and = new ArrayList<>();
			for (TextChannel channel : channels) {
				long channelId = channel.getIdLong();
				and.add(Operators.ne("$$this.id", channelId));

				Document channelData = new Document("id", channelId).append("groups", List.of(groupData));

				Bson channelFilter = Operators.filter(channelMap, Operators.eq("$$this.id", channelId));
				Bson groupMap = Operators.ifNull(Operators.first(Operators.map(channelFilter, "$$this.groups")), Collections.EMPTY_LIST);
				Bson groupFilter = Operators.filter(groupMap, Operators.eq("$$this.group", group));

				concat.add(Operators.cond(Operators.isEmpty(channelFilter), List.of(channelData), Operators.cond(Operators.isEmpty(groupFilter), List.of(Operators.mergeObjects(Operators.first(channelFilter), new Document("groups", Operators.concatArrays(List.of(groupData), Operators.filter(groupMap, Operators.ne("$$this.group", group)))))), List.of(Operators.mergeObjects(Operators.first(channelFilter), new Document("groups", Operators.concatArrays(List.of(Operators.mergeObjects(Operators.first(groupFilter), new Document("strings", Operators.concatArrays(Operators.filter(Operators.first(Operators.map(groupFilter, "$$this.strings")), Operators.ne("$$this", string)), List.of(string))))), Operators.filter(groupMap, Operators.ne("$$this.group", group)))))))));
			}

			concat.add(Operators.filter(channelMap, Operators.and(and)));
			List<Bson> update = List.of(Operators.set("antiRegex.regexes", Operators.cond(Operators.or(Operators.extinct("$antiRegex.regexes"), Operators.isEmpty(regexFilter)), "$antiRegex.regexes", Operators.concatArrays(Operators.filter("$antiRegex.regexes", Operators.ne("$$this.id", id)), List.of(Operators.mergeObjects(Operators.first(regexFilter), new Document("whitelist", new Document("channels", Operators.concatArrays(concat)))))))));

			FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().returnDocument(ReturnDocument.BEFORE);
			this.database.findAndUpdateGuildById(event.getGuild().getIdLong(), update, options).whenComplete((data, exception) -> {
				if (ExceptionUtility.sendExceptionally(event, exception)) {
					return;
				}

				data = data == null ? Database.EMPTY_DOCUMENT : data;

				List<Document> regexes = data.getEmbedded(List.of("antiRegex", "regexes"), Collections.emptyList());
				Document regex = regexes.stream()
					.filter(d -> d.getObjectId("id").equals(id))
					.findFirst()
					.orElse(null);

				if (regex == null) {
					event.replyFailure("I could not find that anti regex").queue();
					return;
				}

				List<Document> whitelists = regex.getEmbedded(List.of("whitelist", "channels"), Collections.emptyList());
				if (!whitelists.isEmpty()) {
					Document whitelist = whitelists.stream()
						.filter(d -> channels.stream().anyMatch(channel -> channel.getIdLong() == d.getLong("id")))
						.findAny()
						.orElse(Database.EMPTY_DOCUMENT);

					List<Document> groups = whitelist.getList("groups", Document.class, Collections.emptyList());
					Document oldGroup = groups.stream()
						.filter(d -> d.getInteger("group") == group)
						.findFirst()
						.orElse(Database.EMPTY_DOCUMENT);

					if (oldGroup.getList("strings", String.class).contains(string)) {
						event.replyFailure("Group **" + group + "** is already whitelisted from that string in all of the provided channels").queue();
						return;
					}
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

			Bson regexFilter = Operators.filter("$antiRegex.regexes", Operators.eq("$$this.id", id));
			Bson channelMap = Operators.ifNull(Operators.first(Operators.map(regexFilter, "$$this.whitelist.channels")), Collections.EMPTY_LIST);

			boolean role = holder instanceof Role;
			long holderId = holder.getIdLong();

			Document holderData = new Document("id", holderId).append("type", role ? HolderType.ROLE.getType() : HolderType.USER.getType());
			List<Bson> concat = new ArrayList<>(), and = new ArrayList<>();
			for (TextChannel channel : channels) {
				long channelId = channel.getIdLong();
				and.add(Operators.ne("$$this.id", channelId));

				Document channelData = new Document("id", channelId).append("holders", List.of(holderData));

				Bson channelFilter = Operators.filter(channelMap, Operators.eq("$$this.id", channelId));
				concat.add(Operators.cond(Operators.isEmpty(channelFilter), List.of(channelData), List.of(Operators.mergeObjects(Operators.first(channelFilter), new Document("holders", Operators.concatArrays(List.of(holderData), Operators.filter(Operators.ifNull(Operators.first(Operators.map(channelFilter, "$$this.holders")), Collections.EMPTY_LIST), Operators.ne("$$this.id", holderId))))))));
			}

			concat.add(Operators.filter(channelMap, Operators.and(and)));
			List<Bson> update = List.of(Operators.set("antiRegex.regexes", Operators.cond(Operators.or(Operators.extinct("$antiRegex.regexes"), Operators.isEmpty(regexFilter)), "$antiRegex.regexes", Operators.concatArrays(Operators.filter("$antiRegex.regexes", Operators.ne("$$this.id", id)), List.of(Operators.mergeObjects(Operators.first(regexFilter), new Document("whitelist", new Document("channels", Operators.concatArrays(concat)))))))));

			FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().returnDocument(ReturnDocument.BEFORE);
			this.database.findAndUpdateGuildById(event.getGuild().getIdLong(), update, options).whenComplete((data, exception) -> {
				if (ExceptionUtility.sendExceptionally(event, exception)) {
					return;
				}

				data = data == null ? Database.EMPTY_DOCUMENT : data;

				List<Document> regexes = data.getEmbedded(List.of("antiRegex", "regexes"), Collections.emptyList());
				Document regex = regexes.stream()
					.filter(d -> d.getObjectId("id").equals(id))
					.findFirst()
					.orElse(null);

				if (regex == null) {
					event.replyFailure("I could not find that anti regex").queue();
					return;
				}

				List<Document> whitelists = regex.getEmbedded(List.of("whitelist", "channels"), Collections.emptyList());
				if (!whitelists.isEmpty()) {
					Document whitelist = whitelists.stream()
						.filter(d -> channels.stream().anyMatch(channel -> channel.getIdLong() == d.getLong("id")))
						.findAny()
						.orElse(Database.EMPTY_DOCUMENT);

					List<Document> holders = whitelist.getList("holders", Document.class, Collections.emptyList());
					if (holders.stream().anyMatch(d -> d.getLong("id") == holderId)) {
						event.replyFailure((role ? ((Role) holder).getAsMention() : ((Member) holder).getUser().getAsMention()) + " is already whitelisted in all of the provided channels").queue();
						return;
					}
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

			Bson regexFilter = Operators.filter("$antiRegex.regexes", Operators.eq("$$this.id", id));
			Bson channelMap = Operators.ifNull(Operators.first(Operators.map(regexFilter, "$$this.whitelist.channels")), Collections.EMPTY_LIST);

			List<Bson> concat = new ArrayList<>(), and = new ArrayList<>();
			for (TextChannel channel : channels) {
				long channelId = channel.getIdLong();
				and.add(Operators.ne("$$this.id", channelId));

				Bson channelFilter = Operators.filter(channelMap, Operators.eq("$$this.id", channelId));
				Bson groupMap = Operators.ifNull(Operators.first(Operators.map(channelFilter, "$$this.groups")), Collections.EMPTY_LIST);
				Bson groupFilter = Operators.filter(groupMap, Operators.eq("$$this.group", group));

				concat.add(Operators.cond(Operators.or(Operators.isEmpty(channelFilter), Operators.isEmpty(groupFilter)), Collections.EMPTY_LIST, List.of(Operators.mergeObjects(Operators.first(channelFilter), new Document("groups", Operators.concatArrays(List.of(Operators.mergeObjects(Operators.first(groupFilter), new Document("strings", Operators.filter(Operators.first(Operators.map(groupFilter, "$$this.strings")), Operators.ne("$$this", string))))), Operators.filter(groupMap, Operators.ne("$$this.group", group))))))));
			}

			concat.add(Operators.filter(channelMap, Operators.and(and)));
			List<Bson> update = List.of(Operators.set("antiRegex.regexes", Operators.cond(Operators.or(Operators.extinct("$antiRegex.regexes"), Operators.isEmpty(regexFilter)), "$antiRegex.regexes", Operators.concatArrays(Operators.filter("$antiRegex.regexes", Operators.ne("$$this.id", id)), List.of(Operators.mergeObjects(Operators.first(regexFilter), new Document("whitelist", new Document("channels", Operators.concatArrays(concat)))))))));

			FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().returnDocument(ReturnDocument.BEFORE);
			this.database.findAndUpdateGuildById(event.getGuild().getIdLong(), update, options).whenComplete((data, exception) -> {
				if (ExceptionUtility.sendExceptionally(event, exception)) {
					return;
				}

				data = data == null ? Database.EMPTY_DOCUMENT : data;

				List<Document> regexes = data.getEmbedded(List.of("antiRegex", "regexes"), Collections.emptyList());
				Document regex = regexes.stream()
					.filter(d -> d.getObjectId("id").equals(id))
					.findFirst()
					.orElse(null);

				if (regex == null) {
					event.replyFailure("I could not find that anti regex").queue();
					return;
				}

				List<Document> whitelists = regex.getEmbedded(List.of("whitelist", "channels"), Collections.emptyList());
				if (!whitelists.isEmpty()) {
					Document whitelist = whitelists.stream()
						.filter(d -> channels.stream().anyMatch(channel -> channel.getIdLong() == d.getLong("id")))
						.findAny()
						.orElse(Database.EMPTY_DOCUMENT);

					List<Document> groups = whitelist.getList("groups", Document.class, Collections.emptyList());
					Document oldGroup = groups.stream()
						.filter(d -> d.getInteger("group") == group)
						.findFirst()
						.orElse(Database.EMPTY_DOCUMENT);

					if (oldGroup.getList("strings", String.class).contains(string)) {
						event.replySuccess("Group **" + group + "** is no longer whitelisted from that string in the provided channels").queue();
						return;
					}
				}

				event.replyFailure("Group **" + group + "** is not whitelisted from that string in any of the provided channels").queue();
			});
		}

		@Command(value="remove", description="Removes a role or user whitelist from channels")
		@CommandId(120)
		@Examples({"anti regex whitelist remove 5f023782ef9eba03390a740c #channel @everyone", "anti regex whitelist remove 5f023782ef9eba03390a740c @Shea#6653"})
		@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
		public void remove(Sx4CommandEvent event, @Argument(value="id") ObjectId id, @Argument(value="channel", nullDefault=true) TextChannel channelArgument, @Argument(value="user | role") IPermissionHolder holder) {
			List<TextChannel> channels = channelArgument == null ? event.getGuild().getTextChannels() : List.of(channelArgument);

			Bson regexFilter = Operators.filter("$antiRegex.regexes", Operators.eq("$$this.id", id));
			Bson channelMap = Operators.ifNull(Operators.first(Operators.map(regexFilter, "$$this.whitelist.channels")), Collections.EMPTY_LIST);

			boolean role = holder instanceof Role;
			long holderId = holder.getIdLong();

			Document holderData = new Document("id", holderId).append("type", role ? HolderType.ROLE.getType() : HolderType.USER.getType());
			List<Bson> concat = new ArrayList<>(), and = new ArrayList<>();
			for (TextChannel channel : channels) {
				long channelId = channel.getIdLong();
				and.add(Operators.ne("$$this.id", channelId));

				Document channelData = new Document("id", channelId).append("holders", List.of(holderData));

				Bson channelFilter = Operators.filter(channelMap, Operators.eq("$$this.id", channelId));
				concat.add(Operators.cond(Operators.isEmpty(channelFilter), List.of(channelData), List.of(Operators.mergeObjects(Operators.first(channelFilter), new Document("holders", Operators.filter(Operators.ifNull(Operators.first(Operators.map(channelFilter, "$$this.holders")), Collections.EMPTY_LIST), Operators.ne("$$this.id", holderId)))))));
			}

			concat.add(Operators.filter(channelMap, Operators.and(and)));
			List<Bson> update = List.of(Operators.set("antiRegex.regexes", Operators.cond(Operators.or(Operators.extinct("$antiRegex.regexes"), Operators.isEmpty(regexFilter)), "$antiRegex.regexes", Operators.concatArrays(Operators.filter("$antiRegex.regexes", Operators.ne("$$this.id", id)), List.of(Operators.mergeObjects(Operators.first(regexFilter), new Document("whitelist", new Document("channels", Operators.concatArrays(concat)))))))));

			FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().returnDocument(ReturnDocument.BEFORE);
			this.database.findAndUpdateGuildById(event.getGuild().getIdLong(), update, options).whenComplete((data, exception) -> {
				if (ExceptionUtility.sendExceptionally(event, exception)) {
					return;
				}

				data = data == null ? Database.EMPTY_DOCUMENT : data;

				List<Document> regexes = data.getEmbedded(List.of("antiRegex", "regexes"), Collections.emptyList());
				Document regex = regexes.stream()
					.filter(d -> d.getObjectId("id").equals(id))
					.findFirst()
					.orElse(null);

				if (regex == null) {
					event.replyFailure("I could not find that anti regex").queue();
					return;
				}

				List<Document> whitelists = regex.getEmbedded(List.of("whitelist", "channels"), Collections.emptyList());
				if (!whitelists.isEmpty()) {
					Document whitelist = whitelists.stream()
						.filter(d -> channels.stream().anyMatch(channel -> channel.getIdLong() == d.getLong("id")))
						.findAny()
						.orElse(Database.EMPTY_DOCUMENT);

					List<Document> holders = whitelist.getList("holders", Document.class, Collections.emptyList());
					if (holders.stream().anyMatch(d -> d.getLong("id") == holderId)) {
						event.replySuccess((role ? ((Role) holder).getAsMention() : ((Member) holder).getUser().getAsMention()) + " is no longer whitelisted in the provided channels").queue();
						return;
					}
				}

				event.replyFailure((role ? ((Role) holder).getAsMention() : ((Member) holder).getUser().getAsMention()) + " is not whitelisted in any of the provided channels").queue();
			});
		}

		// TODO: Think of a good format
		@Command(value="list", description="Lists regex groups, roles and users that are whitelisted from specific channels for an anti regex")
		@CommandId(121)
		@Examples({"anti regex whitelist list 5f023782ef9eba03390a740c"})
		public void list(Sx4CommandEvent event, @Argument(value="id") ObjectId id, @Argument(value="channels") TextChannel channel) {
			List<Document> regexes = this.database.getGuildById(event.getGuild().getIdLong(), Projections.include("antiRegex.regexes")).getEmbedded(List.of("antiRegex", "regexes"), Collections.emptyList());
			Document regex = regexes.stream()
				.filter(data -> data.getObjectId("id").equals(id))
				.findFirst()
				.orElse(null);

			if (regex == null) {
				event.replyFailure("I could not find that regex").queue();
				return;
			}

			List<Document> whitelists = regex.getEmbedded(List.of("whitelist", "channels"), Collections.emptyList());
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
	
	public class TemplateCommand extends Sx4Command {
		
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
				.append("ownerId", event.getAuthor().getIdLong());
			
			this.database.insertRegex(data).whenComplete((result, exception) -> {
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
			List<Document> list = this.database.getRegexes(Filters.eq("approved", true), Projections.include("title", "description", "pattern", "ownerId", "uses")).sort(Sorts.descending("uses")).into(new ArrayList<>());
			if (list.isEmpty()) {
				event.replyFailure("There are no regex templates currently").queue();
				return;
			}

			PagedResult<Document> paged = new PagedResult<>(list)
				.setPerPage(6)
				.setCustomFunction(page -> {
					MessageBuilder builder = new MessageBuilder();

					EmbedBuilder embed = new EmbedBuilder();
					embed.setAuthor("Regex Template List", null, event.getSelfUser().getEffectiveAvatarUrl());
					embed.setTitle("Page " + page.getPage() + "/" + page.getMaxPage());
					embed.setFooter("next | previous | go to <page_number> | cancel", null);

					page.forEach((data, index) -> {
						User owner = event.getShardManager().getUserById(data.getLong("ownerId"));
						List<Long> uses = data.getList("uses", Long.class, Collections.emptyList());

						embed.addField(data.getString("title"), String.format("Id: %s\nRegex: `%s`\nUses: %,d\nOwner: %s\nDescription: %s", data.getObjectId("_id").toHexString(), data.getString("pattern"), uses.size(), owner == null ? "Annonymous#0000" : owner.getAsTag(), data.getString("description")), true);
					});

					return builder.setEmbed(embed.build()).build();
				});

			paged.execute(event);
		}
		
		@Command(value="queue", description="View the queue of regexes yet to be denied or approved")
		@CommandId(125)
		@Examples({"anti regex template queue"})
		public void queue(Sx4CommandEvent event) {
			List<Document> queue = this.database.getRegexes(Filters.ne("approved", true), Projections.include("title", "description", "pattern", "ownerId")).into(new ArrayList<>());
			if (queue.isEmpty()) {
				event.replyFailure("There are now regex templates in the queue").queue();
				return;
			}

			PagedResult<Document> paged = new PagedResult<>(queue)
				.setPerPage(6)
				.setCustomFunction(page -> {
					MessageBuilder builder = new MessageBuilder();
					
					EmbedBuilder embed = new EmbedBuilder();
					embed.setAuthor("Regex Template Queue", null, event.getSelfUser().getEffectiveAvatarUrl());
					embed.setTitle("Page " + page.getPage() + "/" + page.getMaxPage());
					embed.setFooter("next | previous | go to <page_number> | cancel", null);
					
					page.forEach((data, index) -> {
						User owner = event.getShardManager().getUserById(data.getLong("ownerId"));

						embed.addField(data.getString("title"), String.format("Id: %s\nRegex: `%s`\nOwner: %s\nDescription: %s", data.getObjectId("_id").toHexString(), data.getString("pattern"), owner == null ? "Annonymous#0000" : owner.getAsTag(), data.getString("description")), true);
					});
					
					return builder.setEmbed(embed.build()).build();
				});
			
			paged.execute(event);
		}
		
		@Command(value="approve", description="Approve a regex in the queue")
		@CommandId(126)
		@Examples({"anti regex template approve 5f023782ef9eba03390a740c"})
		@Developer
		public void approve(Sx4CommandEvent event, @Argument(value="id") ObjectId id) {
			FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().returnDocument(ReturnDocument.BEFORE).projection(Projections.include("ownerId", "title"));
			this.database.findAndUpdateRegexById(id, Updates.set("approved", true), options).whenComplete((data, exception) -> {
				if (ExceptionUtility.sendExceptionally(event, exception)) {
					return;
				}
				
				if (data == null) {
					event.replyFailure("I could not find that regex template").queue();
					return;
				}
				
				User owner = event.getShardManager().getUserById(data.getLong("ownerId"));
				if (owner != null) {
					owner.openPrivateChannel()
						.flatMap(channel -> channel.sendMessage("Your regex template **" + data.getString("title") + "** was just approved you can now use it in anti regex " + this.config.getSuccessEmote()))
						.queue(null, ErrorResponseException.ignore(ErrorResponse.CANNOT_SEND_TO_USER));
				}
				
				event.replySuccess("That regex template has been approved").queue();
			});
		}
		
		@Command(value="deny", description="Denies a regex in the queue")
		@CommandId(127)
		@Examples({"anti regex template deny 5f023782ef9eba03390a740c"})
		@Developer
		public void deny(Sx4CommandEvent event, @Argument(value="id") ObjectId id, @Argument(value="reason", endless=true) String reason) {
			FindOneAndDeleteOptions options = new FindOneAndDeleteOptions().projection(Projections.include("ownerId", "title"));
			this.database.findAndDeleteRegexById(id, options).whenComplete((data, exception) -> {
				if (ExceptionUtility.sendExceptionally(event, exception)) {
					return;
				}
				
				if (data == null) {
					event.replyFailure("I could not find that regex template").queue();
					return;
				}
				
				User owner = event.getShardManager().getUserById(data.getLong("ownerId"));
				if (owner != null) {
					owner.openPrivateChannel()
						.flatMap(channel -> channel.sendMessage("Your regex template **" + data.getString("title") + "** was just denied with the reason `" + reason + "` " + this.config.getFailureEmote()))
						.queue(null, ErrorResponseException.ignore(ErrorResponse.CANNOT_SEND_TO_USER));
				}
				
				event.replySuccess("That regex template has been denied").queue();
			});
		}
		
	}
	
}
