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
import com.sx4.bot.database.model.Operators;
import com.sx4.bot.paged.PagedResult;
import com.sx4.bot.utility.ExceptionUtility;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
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
		super.setCategory(Category.AUTO_MODERATION);
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

			List<Document> regexes = this.database.getGuildById(event.getGuild().getIdLong(), Projections.include("antiRegex.regexes")).getEmbedded(List.of("antiRegex", "regexes"), Collections.emptyList());
			Document regex = regexes.stream()
				.filter(data -> data.getObjectId("id").equals(id))
				.findFirst()
				.orElse(null);

			if (regex == null) {
				event.reply("I could not find that regex " + this.config.getFailureEmote()).queue();
				return;
			}

			int groupCount = Pattern.compile(regex.getString("pattern")).matcher("").groupCount();
			if (group > groupCount) {
				event.reply("That regex does not have a group " + group + " " + this.config.getFailureEmote()).queue();
				return;
			}

			List<Document> whitelists = regex.getEmbedded(List.of("whitelist", "channels"), Collections.emptyList());
			List<Document> whitelistsCopy = new ArrayList<>(whitelists);

			Document groupWhitelist = new Document("group", group).append("string", string);
			Channels : for (TextChannel channel : channels) {
				long channelId = channel.getIdLong();
				for (Document whitelist : whitelists) {
					if (whitelist.get("id", 0L) == channelId) {
						List<Document> groups = whitelist.getList("groups", Document.class, Collections.emptyList());
						if (groups.stream().anyMatch(data -> data.get("group", 0) == group && data.getString("string").equals(string))) {
							continue;
						}

						groups.add(groupWhitelist);
						whitelistsCopy.remove(whitelist);

						whitelistsCopy.add(
							new Document("id", channelId)
								.append("groups", groups)
						);

						continue Channels;
					}
				}

				whitelistsCopy.add(
					new Document("id", channelId)
						.append("groups", List.of(groupWhitelist))
				);
			}

			UpdateOptions options = new UpdateOptions().arrayFilters(List.of(Filters.eq("regex.id", id)));
			this.database.updateGuildById(event.getGuild().getIdLong(), Updates.set("antiRegex.regexes.$[regex].whitelist.channels", whitelistsCopy), options).whenComplete((result, exception) -> {
				if (ExceptionUtility.sendExceptionally(event, exception)) {
					return;
				}

				if (result.getModifiedCount() == 0) {
					event.reply("Group **" + group + "** already had that string whitelisted in the channels provided " + this.config.getFailureEmote()).queue();
					return;
				}

				event.reply("Group **" + group + "** is now whitelisted from that string in the provided channel " + this.config.getSuccessEmote()).queue();
			});
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
