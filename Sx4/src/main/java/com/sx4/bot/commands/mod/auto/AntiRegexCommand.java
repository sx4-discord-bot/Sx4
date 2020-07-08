package com.sx4.bot.commands.mod.auto;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.command.Command;
import com.jockie.bot.core.command.Command.Developer;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndDeleteOptions;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Updates;
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
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;

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
		
		List<Bson> update = List.of(Operators.set("antiRegex.regexes", Operators.cond(Operators.and(Operators.exists("$antiRegex.regexes"), Operators.isEmpty(Operators.filter("$antiRegex.regexes", Operators.eq("$$this.id", id)))), Operators.concatArrays("$antiRegex.regexes", List.of(data)), "$antiRegex.regexes")));
		
		this.database.updateGuildById(event.getGuild().getIdLong(), update)
			.thenCompose(result -> {
				if (result.getModifiedCount() == 0) {
					event.reply("You already have that regex setup in this server " + this.config.getFailureEmote()).queue();
					return CompletableFuture.completedFuture(null);
				}
				
				return this.database.updateRegexById(id, Updates.addToSet("guilds", event.getGuild().getIdLong()));
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
				
				return this.database.updateRegexById(id, Updates.pull("guilds", event.getGuild().getIdLong()));
			}).whenComplete((result, exception) -> {
				if (ExceptionUtility.sendExceptionally(event, exception) || result == null) {
					return;
				}
				
				event.reply("That regex is no longer active " + this.config.getSuccessEmote()).queue();
			});
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
			
			String patternString = pattern.toString();
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
		
		@Command(value="queue", description="View the queue of regexes yet to be denied or approved")
		@Examples({"anti regex template queue"})
		public void queue(Sx4CommandEvent event) {
			List<Document> queue = this.database.getRegexes(Filters.ne("approved", true), Projections.include("title", "description", "pattern", "ownerId")).into(new ArrayList<>());
			
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
