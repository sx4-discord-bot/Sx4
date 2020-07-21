package com.sx4.bot.commands.mod.auto;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.command.Command;
import com.jockie.bot.core.command.Command.Developer;
import com.mongodb.client.model.*;
import com.sx4.bot.annotations.argument.Limit;
import com.sx4.bot.annotations.command.AuthorPermissions;
import com.sx4.bot.annotations.command.Examples;
import com.sx4.bot.category.Category;
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
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

public class AntiRegexCommand extends Sx4Command {

	public AntiRegexCommand() {
		super("anti regex");
		
		super.setAliases("antiregex");
		super.setDescription("Setup a regex which if matched with the content of a message it will perform an action");
		super.setExamples("anti regex add", "anti regex remove", "anti regex list");
		super.setCategoryAll(Category.AUTO_MODERATION);
	}
	
	public void onCommand(Sx4CommandEvent event) {
		event.replyHelp().queue();
	}
	
	@Command(value="add", description="Add a regex from `anti regex template list` to be checked on every message")
	@Examples({"anti regex add 5f023782ef9eba03390a740c"})
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	public void add(Sx4CommandEvent event, @Argument(value="id") ObjectId id) {
		Document regex = this.database.getRegexById(id, Projections.include("approved", "pattern", "title"));
		if (!regex.getBoolean("approved", false)) {
			event.reply("I could not find that regex template " + this.config.getFailureEmote()).queue();
			return;
		}
		
		Document data = new Document("id", id)
			.append("pattern", regex.getString("pattern"));
		
		List<Bson> update = List.of(Operators.set("antiRegex.regexes", Operators.cond(Operators.and(Operators.exists("$antiRegex.regexes"), Operators.not(Operators.isEmpty(Operators.filter("$antiRegex.regexes", Operators.eq("$$this.id", id))))), "$antiRegex.regexes", Operators.cond(Operators.exists("$antiRegex.regexes"), Operators.concatArrays("$antiRegex.regexes", List.of(data)), List.of(data)))));

		this.database.updateGuildById(event.getGuild().getIdLong(), update)
			.thenCompose(result -> {
				if (result.getModifiedCount() == 0) {
					event.reply("You already have that regex setup in this server " + this.config.getFailureEmote()).queue();
					return CompletableFuture.completedFuture(null);
				}

				return this.database.updateRegexById(id, Updates.addToSet("uses", event.getGuild().getIdLong()));
			}).whenComplete((result, exception) -> {
				if (ExceptionUtility.sendExceptionally(event, exception) || result == null) {
					return;
				}

				event.reply("The regex **" + regex.getString("title") + "** is now active " + this.config.getSuccessEmote()).queue();
			});
	}

	@Command(value="remove", description="Removes a anti regex that you have setup")
	@Examples({"anti regex remove 5f023782ef9eba03390a740c"})
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	public void remove(Sx4CommandEvent event, @Argument(value="id") ObjectId id) {
		this.database.updateGuildById(event.getGuild().getIdLong(), Updates.pull("antiRegex.regexes", Filters.eq("id", id)))
			.thenCompose(result -> {
				if (result.getModifiedCount() == 0) {
					event.reply("You do that have that regex setup in this server " + this.config.getFailureEmote()).queue();
					return CompletableFuture.completedFuture(null);
				}

				return this.database.updateRegexById(id, Updates.pull("uses", event.getGuild().getIdLong()));
			}).whenComplete((result, exception) -> {
				if (ExceptionUtility.sendExceptionally(event, exception) || result == null) {
					return;
				}

				event.reply("That regex is no longer active " + this.config.getSuccessEmote()).queue();
			});
	}

	@Command(value="match action", aliases={"matchaction"}, description="Set what the bot should do when the regex is matched")
	@Examples({"anti regex match action 5f023782ef9eba03390a740c SEND_MESSAGE", "anti regex match action 5f023782ef9eba03390a740c SEND_MESSAGE DELETE_MESSAGE"})
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	public void matchAction(Sx4CommandEvent event, @Argument(value="id") ObjectId id, @Argument(value="actions") MatchAction... actions) {
		long raw = MatchAction.getRaw(actions);

		Bson filter = Operators.filter("$antiRegex.regexes", Operators.eq("$$this.id", id));
		List<Bson> update = List.of(Operators.set("antiRegex.regexes", Operators.cond(Operators.or(Operators.extinct("$antiRegex.regexes"), Operators.isEmpty(filter)), "$antiRegex.regexes", Operators.concatArrays(List.of(Operators.mergeObjects(Operators.first(filter), new Document("action", Operators.mergeObjects(Operators.ifNull(Operators.first(Operators.map(filter, "$$this.action")), Database.EMPTY_DOCUMENT), new Document("match", raw))))), Operators.filter("$antiRegex.regexes", Operators.ne("$$this.id", id))))));

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
				event.reply("I could not find that regex " + this.config.getFailureEmote()).queue();
				return;
			}

			long matchActionRaw = regex.getEmbedded(List.of("action", "match"), MatchAction.ALL);
			if (matchActionRaw == raw) {
				event.reply("Your match action for this regex is already set to that " + this.config.getFailureEmote()).queue();
				return;
			}

			event.reply("Your match action for that regex has been updated " + this.config.getSuccessEmote()).queue();
		});
	}


	// TODO
	@Command(value="mod action", description="Sets the action to be taken when a user hits the max attempts")
	@Examples({"anti regex mod action 5f023782ef9eba03390a740c WARN", "anti regex mod action 5f023782ef9eba03390a740c MUTE 60m"})
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	public void modAction(Sx4CommandEvent event, @Argument(value="id") ObjectId id /*, @Argument(value="action", endless=true) TimedArgument<ModAction> timedAction*/) {
		TimedArgument<ModAction> timedAction = new TimedArgument<>(null, ModAction.MUTE);
		ModAction action = timedAction.getArgument();
		if (!action.isOffence()) {
			event.reply("The action has to be an offence " + this.config.getFailureEmote()).queue();
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
		Bson attemptsMap = Operators.first(Operators.map(filter, "$$this.action.mod.attempts"));
		List<Bson> update = List.of(Operators.set("antiRegex.regexes", Operators.cond(Operators.or(Operators.extinct("$antiRegex.regexes"), Operators.isEmpty(filter)), "$antiRegex.regexes", Operators.concatArrays(List.of(Operators.mergeObjects(Operators.first(filter), new Document("action", Operators.mergeObjects(Operators.ifNull(Operators.first(Operators.map(filter, "$$this.action")), Database.EMPTY_DOCUMENT), new Document("mod", Operators.mergeObjects(modAction, Operators.cond(Operators.isNull(attemptsMap), Database.EMPTY_DOCUMENT, new Document("attempts", attemptsMap)))))))), Operators.filter("$antiRegex.regexes", Operators.ne("$$this.id", id))))));

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
				event.reply("I could not find that regex " + this.config.getFailureEmote()).queue();
				return;
			}

			Document modActionData = regex.getEmbedded(List.of("action", "mod"), Document.class);
			if (modActionData != null && modActionData.get("type", 0) == action.getType() && modActionData.get("duration", 0L) == duration) {
				event.reply("Your mod action for this regex is already set to that " + this.config.getFailureEmote()).queue();
				return;
			}

			event.reply("Your mod action for that regex has been updated " + this.config.getSuccessEmote()).queue();
		});
	}

	@Command(value="attempts", description="Sets the amount of attempts needed for the mod action to execute")
	@Examples({"anti regex attempts 5f023782ef9eba03390a740c 3", "anti regex attempts 5f023782ef9eba03390a740c 1"})
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	public void attempts(Sx4CommandEvent event, @Argument(value="id") ObjectId id, @Argument(value="attempts") @Limit(min=1) int attempts) {
		Bson filter = Operators.filter("$antiRegex.regexes", Operators.eq("$$this.id", id));
		Bson modActionMap = Operators.first(Operators.map(filter, "$$this.action.mod"));
		List<Bson> update = List.of(Operators.set("antiRegex.regexes", Operators.cond(Operators.or(Operators.extinct("$antiRegex.regexes"), Operators.isEmpty(filter), Operators.isNull(modActionMap)), "$antiRegex.regexes", Operators.concatArrays(List.of(Operators.mergeObjects(Operators.first(filter), new Document("action", Operators.mergeObjects(Operators.ifNull(Operators.first(Operators.map(filter, "$$this.action")), Database.EMPTY_DOCUMENT), new Document("mod", Operators.mergeObjects(modActionMap, new Document("attempts", attempts))))))), Operators.filter("$antiRegex.regexes", Operators.ne("$$this.id", id))))));

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
				event.reply("I could not find that regex " + this.config.getFailureEmote()).queue();
				return;
			}

			Document modActionData = regex.getEmbedded(List.of("action", "mod"), Document.class);
			if (modActionData == null) {
				event.reply("You need a mod action to be set up to change the attempts " + this.config.getFailureEmote()).queue();
				return;
			}

			if (modActionData.get("attempts", 3) == attempts) {
				event.reply("Your attempts are already set to **" + attempts + "** " + this.config.getFailureEmote()).queue();
				return;
			}

			event.reply("Attempts to a mod action have been set to **" + attempts + "** " + this.config.getSuccessEmote()).queue();
		});
	}

	@Command(value="message", description="Changes the message which is sent when someone triggers an anti regex")
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	@Examples({"anti regex message 5f023782ef9eba03390a740c You cannot have a url in your message :no_entry:", "anti regex message 5f023782ef9eba03390a740c {user.mention}, don't send that here or else you'll get a {regex.action} :no_entry:"})
	public void message(Sx4CommandEvent event, @Argument(value="id") ObjectId id, @Argument(value="message", endless=true) String message) {
		if (message.length() > 1500) {
			event.reply("Your message cannot be longer than 1500 characters :no_entry:").queue();
			return;
		}

		Bson filter = Operators.filter("$antiRegex.regexes", Operators.eq("$$this.id", id));
		List<Bson> update = List.of(Operators.set("antiRegex.regexes", Operators.cond(Operators.or(Operators.extinct("$antiRegex.regexes"), Operators.isEmpty(filter)), "$antiRegex.regexes", Operators.concatArrays(Operators.filter("$antiRegex.regexes", Operators.ne("$$this.id", id)), List.of(Operators.mergeObjects(Operators.first(filter), new Document("message", message)))))));

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
				event.reply("I could not find that regex " + this.config.getFailureEmote()).queue();
				return;
			}

			String oldMessage = regex.get("message", AntiRegexManager.DEFAULT_MESSAGE);
			if (oldMessage.equals(message)) {
				event.reply("Your message for that regex was already set to that " + this.config.getFailureEmote()).queue();
				return;
			}

			event.reply("Your message for that regex has been updated " + this.config.getSuccessEmote()).queue();
		});
	}

	@Command(value="list", description="Lists the regexes which are active in this server")
	@Examples({"anti regex list"})
	public void list(Sx4CommandEvent event) {
		List<Document> regexes = this.database.getGuildById(event.getGuild().getIdLong(), Projections.include("antiRegex.regexes")).getEmbedded(List.of("antiRegex", "regexes"), Collections.emptyList());
		if (regexes.isEmpty()) {
			event.reply("There are no regexes setup in this server " + this.config.getFailureEmote()).queue();
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

	public class WhitelistCommand extends Sx4Command {

		public WhitelistCommand() {
			super("whitelist");

			super.setDescription("Whitelist roles and users from certain channels so they can ignore the anti regex");
			super.setExamples("anti regex whitelist add", "anti regex whitelist remove");
		}

		public void onCommand(Sx4CommandEvent event) {
			event.replyHelp().queue();
		}

		@Command(value="add", description="Adds a whitelist for a group in the regex")
		@Examples({"anti regex whitelist add 5f023782ef9eba03390a740c #youtube-links 2 youtube.com", "anti regex whitelist add 5f023782ef9eba03390a740c 0 https://youtube.com"})
		@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
		public void add(Sx4CommandEvent event, @Argument(value="id") ObjectId id, @Argument(value="channel", nullDefault=true) TextChannel channelArgument, @Argument(value="group") @Limit(min=0) int group, @Argument(value="string", endless=true) String string) {
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
					event.reply("I could not find that anti regex " + this.config.getFailureEmote()).queue();
					return;
				}

				List<Document> whitelists = regex.getEmbedded(List.of("whitelist", "channels"), Collections.emptyList());
				if (!whitelists.isEmpty()) {
					Document whitelist = whitelists.stream()
						.filter(d -> channels.stream().anyMatch(channel -> channel.getIdLong() == d.get("id", 0L)))
						.findAny()
						.orElse(null);

					List<Document> groups = whitelist.getList("groups", Document.class, Collections.emptyList());
					Document oldGroup = groups.stream()
						.filter(d -> d.get("group", 0) == group)
						.findFirst()
						.orElse(null);

					if (oldGroup.getList("strings", String.class).contains(string)) {
						event.reply("Group **" + group + "** is already whitelisted from that string in all of the provided channels " + this.config.getFailureEmote()).queue();
						return;
					}
				}

				event.reply("Group **" + group + "** is now whitelisted from that string in the provided channels " + this.config.getSuccessEmote()).queue();
			});
		}

		@Command(value="add", description="Adds a whitelist for a role or user")
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
					event.reply("I could not find that anti regex " + this.config.getFailureEmote()).queue();
					return;
				}

				List<Document> whitelists = regex.getEmbedded(List.of("whitelist", "channels"), Collections.emptyList());
				if (!whitelists.isEmpty()) {
					Document whitelist = whitelists.stream()
						.filter(d -> channels.stream().anyMatch(channel -> channel.getIdLong() == d.get("id", 0L)))
						.findAny()
						.orElse(null);

					List<Document> holders = whitelist.getList("holders", Document.class, Collections.emptyList());
					if (holders.stream().anyMatch(d -> d.get("id", 0L) == holderId)) {
						event.reply((role ? ((Role) holder).getAsMention() : ((Member) holder).getUser().getAsMention()) + " is already whitelisted in all of the provided channels " + this.config.getFailureEmote()).queue();
						return;
					}
				}

				event.reply((role ? ((Role) holder).getAsMention() : ((Member) holder).getUser().getAsMention()) + " is now whitelisted in the provided channels " + this.config.getSuccessEmote()).queue();
			});
		}

		@Command(value="remove", description="Removes a group whitelist from channels")
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
					event.reply("I could not find that anti regex " + this.config.getFailureEmote()).queue();
					return;
				}

				List<Document> whitelists = regex.getEmbedded(List.of("whitelist", "channels"), Collections.emptyList());
				if (!whitelists.isEmpty()) {
					Document whitelist = whitelists.stream()
						.filter(d -> channels.stream().anyMatch(channel -> channel.getIdLong() == d.get("id", 0L)))
						.findAny()
						.orElse(null);

					List<Document> groups = whitelist.getList("groups", Document.class, Collections.emptyList());
					Document oldGroup = groups.stream()
						.filter(d -> d.get("group", 0) == group)
						.findFirst()
						.orElse(null);

					if (oldGroup.getList("strings", String.class).contains(string)) {
						event.reply("Group **" + group + "** is no longer whitelisted from that string in the provided channels " + this.config.getSuccessEmote()).queue();
						return;
					}
				}

				event.reply("Group **" + group + "** is not whitelisted from that string in any of the provided channels " + this.config.getFailureEmote()).queue();
			});
		}

		@Command(value="remove", description="Removes a role or user whitelist from channels")
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
					event.reply("I could not find that anti regex " + this.config.getFailureEmote()).queue();
					return;
				}

				List<Document> whitelists = regex.getEmbedded(List.of("whitelist", "channels"), Collections.emptyList());
				if (!whitelists.isEmpty()) {
					Document whitelist = whitelists.stream()
						.filter(d -> channels.stream().anyMatch(channel -> channel.getIdLong() == d.get("id", 0L)))
						.findAny()
						.orElse(null);

					List<Document> holders = whitelist.getList("holders", Document.class, Collections.emptyList());
					if (holders.stream().anyMatch(d -> d.get("id", 0L) == holderId)) {
						event.reply((role ? ((Role) holder).getAsMention() : ((Member) holder).getUser().getAsMention()) + " is no longer whitelisted in the provided channels " + this.config.getSuccessEmote()).queue();
						return;
					}
				}

				event.reply((role ? ((Role) holder).getAsMention() : ((Member) holder).getUser().getAsMention()) + " is not whitelisted in any of the provided channels " + this.config.getFailureEmote()).queue();
			});
		}

		// TODO: Think of a good format
		@Command(value="list", description="Lists regex groups, roles and users that are whitelisted from specific channels for an anti regex")
		@Examples({"anti regex whitelist list 5f023782ef9eba03390a740c"})
		public void list(Sx4CommandEvent event, @Argument(value="id") ObjectId id, @Argument(value="channels") TextChannel channel) {
			List<Document> regexes = this.database.getGuildById(event.getGuild().getIdLong(), Projections.include("antiRegex.regexes")).getEmbedded(List.of("antiRegex", "regexes"), Collections.emptyList());
			Document regex = regexes.stream()
				.filter(data -> data.getObjectId("id").equals(id))
				.findFirst()
				.orElse(null);

			if (regex == null) {
				event.reply("I could not find that regex " + this.config.getFailureEmote()).queue();
				return;
			}

			List<Document> whitelists = regex.getEmbedded(List.of("whitelist", "channels"), Collections.emptyList());
			Document whitelist = whitelists.stream()
				.filter(d -> d.get("id", 0L) == channel.getIdLong())
				.findFirst()
				.orElse(null);

			if (whitelist == null) {
				event.reply("Nothing is whitelisted in that channel " + this.config.getFailureEmote()).queue();
				return;
			}

			
		}

	}
	
	public class TemplateCommand extends Sx4Command {
		
		public TemplateCommand() {
			super("template");
			
			super.setDescription("Create regex templates for anti regex");
			super.setExamples("anti regex template add", "anti regex template list");
		}
		
		public void onCommand(Sx4CommandEvent event) {
			event.replyHelp().queue();
		}
		
		@Command(value="add", description="Add a regex to the templates for anyone to use")
		@Examples({"anti regex template add Numbers .*[0-9]+.* Will match any message which contains a number"})
		public void add(Sx4CommandEvent event, @Argument(value="title") String title, @Argument(value="regex") Pattern pattern, @Argument(value="description", endless=true) String description) {
			if (title.length() > 20) {
				event.reply("The title cannot be more than 20 characters " + this.config.getFailureEmote()).queue();
				return;
			}
			
			if (description.length() > 250) {
				event.reply("The description cannot be more than 250 characters " + this.config.getFailureEmote()).queue();
				return;
			}
			
			String patternString = pattern.pattern();
			if (patternString.length() > 200) {
				event.reply("The regex cannot be more than 200 characters " + this.config.getFailureEmote()).queue();
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
				
				event.reply("Your regex has been added to the queue you will be notified when it has been approved or denied " + this.config.getSuccessEmote()).queue();
			});
		}

		@Command(value="list", description="Lists the regexes which you can use for anti regex")
		@Examples({"anti regex template list"})
		public void list(Sx4CommandEvent event) {
			List<Document> list = this.database.getRegexes(Filters.eq("approved", true), Projections.include("title", "description", "pattern", "ownerId", "uses")).sort(Sorts.descending("uses")).into(new ArrayList<>());
			if (list.isEmpty()) {
				event.reply("There are no regex templates currently " + this.config.getFailureEmote()).queue();
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
						User owner = event.getShardManager().getUserById(data.get("ownerId", 0L));
						List<Long> uses = data.getList("uses", Long.class, Collections.emptyList());

						embed.addField(data.getString("title"), String.format("Id: %s\nRegex: `%s`\nUses: %,d\nOwner: %s\nDescription: %s", data.getObjectId("_id").toHexString(), data.getString("pattern"), uses.size(), owner == null ? "Annonymous#0000" : owner.getAsTag(), data.getString("description")), true);
					});

					return builder.setEmbed(embed.build()).build();
				});

			paged.execute(event);
		}
		
		@Command(value="queue", description="View the queue of regexes yet to be denied or approved")
		@Examples({"anti regex template queue"})
		public void queue(Sx4CommandEvent event) {
			List<Document> queue = this.database.getRegexes(Filters.ne("approved", true), Projections.include("title", "description", "pattern", "ownerId")).into(new ArrayList<>());
			if (queue.isEmpty()) {
				event.reply("There are now regex templates in the queue " + this.config.getFailureEmote()).queue();
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
						User owner = event.getShardManager().getUserById(data.get("ownerId", 0L));

						embed.addField(data.getString("title"), String.format("Id: %s\nRegex: `%s`\nOwner: %s\nDescription: %s", data.getObjectId("_id").toHexString(), data.getString("pattern"), owner == null ? "Annonymous#0000" : owner.getAsTag(), data.getString("description")), true);
					});
					
					return builder.setEmbed(embed.build()).build();
				});
			
			paged.execute(event);
		}
		
		@Command(value="approve", description="Approve a regex in the queue")
		@Examples({"anti regex template approve 5f023782ef9eba03390a740c"})
		@Developer
		public void approve(Sx4CommandEvent event, @Argument(value="id") ObjectId id) {
			FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().returnDocument(ReturnDocument.BEFORE).projection(Projections.include("ownerId", "title"));
			this.database.findAndUpdateRegexById(id, Updates.set("approved", true), options).whenComplete((data, exception) -> {
				if (ExceptionUtility.sendExceptionally(event, exception)) {
					return;
				}
				
				if (data == null) {
					event.reply("I could not find that regex template " + this.config.getFailureEmote()).queue();
					return;
				}
				
				User owner = event.getShardManager().getUserById(data.get("ownerId", 0L));
				if (owner != null) {
					owner.openPrivateChannel()
						.flatMap(channel -> channel.sendMessage("Your regex template **" + data.getString("title") + "** was just approved you can now use it in anti regex " + this.config.getSuccessEmote()))
						.queue(null, ErrorResponseException.ignore(ErrorResponse.CANNOT_SEND_TO_USER));
				}
				
				event.reply("That regex template has been approved " + this.config.getSuccessEmote()).queue();
			});
		}
		
		@Command(value="deny", description="Denies a regex in the queue")
		@Examples({"anti regex template deny 5f023782ef9eba03390a740c"})
		@Developer
		public void deny(Sx4CommandEvent event, @Argument(value="id") ObjectId id, @Argument(value="reason", endless=true) String reason) {
			FindOneAndDeleteOptions options = new FindOneAndDeleteOptions().projection(Projections.include("ownerId", "title"));
			this.database.findAndDeleteRegexById(id, options).whenComplete((data, exception) -> {
				if (ExceptionUtility.sendExceptionally(event, exception)) {
					return;
				}
				
				if (data == null) {
					event.reply("I could not find that regex template " + this.config.getFailureEmote()).queue();
					return;
				}
				
				User owner = event.getShardManager().getUserById(data.get("ownerId", 0L));
				if (owner != null) {
					owner.openPrivateChannel()
						.flatMap(channel -> channel.sendMessage("Your regex template **" + data.getString("title") + "** was just denied with the reason `" + reason + "` " + this.config.getFailureEmote()))
						.queue(null, ErrorResponseException.ignore(ErrorResponse.CANNOT_SEND_TO_USER));
				}
				
				event.reply("That regex template has been denied " + this.config.getSuccessEmote()).queue();
			});
		}
		
	}
	
}
