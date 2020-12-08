package com.sx4.bot.commands.management;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.command.Command;
import com.mongodb.client.model.*;
import com.sx4.bot.annotations.argument.Colour;
import com.sx4.bot.annotations.command.AuthorPermissions;
import com.sx4.bot.annotations.command.BotPermissions;
import com.sx4.bot.annotations.command.Examples;
import com.sx4.bot.annotations.command.Redirects;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.database.Database;
import com.sx4.bot.database.model.Operators;
import com.sx4.bot.entities.argument.All;
import com.sx4.bot.entities.argument.MessageArgument;
import com.sx4.bot.entities.management.SuggestionState;
import com.sx4.bot.utility.CheckUtility;
import com.sx4.bot.utility.ColourUtility;
import com.sx4.bot.utility.ExceptionUtility;
import com.sx4.bot.waiter.Waiter;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

public class SuggestionCommand extends Sx4Command {

	public SuggestionCommand() {
		super("suggestion");
		
		super.setDescription("Create a suggestion channel where suggestions can be sent in and voted on in your server");
		super.setExamples("suggestion add", "suggestion set", "suggestion remove");
		super.setCategoryAll(ModuleCategory.MANAGEMENT);
	}
	
	private MessageEmbed getSuggestionEmbed(User author, User moderator, String suggestion, String reason, SuggestionState state) {
		EmbedBuilder embed = new EmbedBuilder()
			.setAuthor(author == null ? "Anonymous#0000" : author.getAsTag(), null, author == null ? null : author.getEffectiveAvatarUrl())
			.setDescription(suggestion)
			.setFooter(state.getName())
			.setColor(state.getColour())
			.setTimestamp(Instant.now());
		
		if (moderator != null) {
			embed.addField("Moderator", moderator.getAsTag(), true);
		}
		
		if (reason != null) {
			embed.addField("Reason", reason, true);
		}
		
		return embed.build();
	}
	
	public void onCommand(Sx4CommandEvent event) {
		event.replyHelp().queue();
	}
	
	@Command(value="toggle", description="Enables/disables suggestions in this server")
	@Examples({"suggestion toggle"})
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	public void toggle(Sx4CommandEvent event) {
		List<Bson> update = List.of(Operators.set("suggestion.enabled", Operators.cond("$suggestion.enabled", Operators.REMOVE, true)));
		this.database.findAndUpdateGuildById(event.getGuild().getIdLong(), Projections.include("suggestion.enabled"), update).whenComplete((data, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}
			
			event.replySuccess("Suggestions are now **" + (data.getEmbedded(List.of("suggestion", "enabled"), false) ? "enabled" : "disabled") + "**").queue();
		});
	}
	
	@Command(value="channel", description="Sets the channel where suggestions are set to")
	@Examples({"suggestion channel", "suggestion channel #suggestions"})
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	public void channel(Sx4CommandEvent event, @Argument(value="channel", endless=true, nullDefault=true) TextChannel channel) {
		List<Bson> update = List.of(Operators.set("suggestion.channelId", channel == null ? Operators.REMOVE : channel.getIdLong()));
		this.database.updateGuildById(event.getGuild().getIdLong(), update).whenComplete((result, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}
			
			if (result.getModifiedCount() == 0) {
				event.replyFailure("The suggestion channel is already " + (channel == null ? "unset" : "set to " + channel.getAsMention())).queue();
				return;
			}
			
			event.replySuccess("The suggestion channel has been " + (channel == null ? "unset" : "set to " + channel.getAsMention())).queue();
		});
	}
	
	@Command(value="add", description="Sends a suggestion to the suggestion channel if one is setup in the server")
	@Redirects({"suggest"})
	@Examples({"suggestion add Add the dog emote", "suggestion Add a channel for people looking to play games"})
	@BotPermissions(permissions={Permission.MESSAGE_ADD_REACTION, Permission.MESSAGE_EMBED_LINKS})
	public void add(Sx4CommandEvent event, @Argument(value="suggestion", endless=true) String suggestion) {
		Document data = this.database.getGuildById(event.getGuild().getIdLong(), Projections.include("suggestion.channelId", "suggestion.enabled")).get("suggestion", Database.EMPTY_DOCUMENT);
		
		if (!data.getBoolean("enabled", false)) {
			event.replyFailure("Suggestions are not enabled in this server").queue();
			return;
		}
		
		long channelId = data.getLong("channelId");
		if (channelId == 0L) {
			event.replyFailure("There is no suggestion channel").queue();
			return;
		}
		
		TextChannel channel = event.getGuild().getTextChannelById(channelId);
		if (channel == null) {
			event.replyFailure("The suggestion channel no longer exists").queue();
			return;
		}
		
		SuggestionState state = SuggestionState.PENDING;
		channel.sendMessage(this.getSuggestionEmbed(event.getAuthor(), null, suggestion, null, state)).queue(message -> {
			Document suggestionData = new Document("id", message.getIdLong())
				.append("channelId", channel.getIdLong())
				.append("authorId", event.getAuthor().getIdLong())
				.append("state", state.getDataName())
				.append("suggestion", suggestion);
			
			this.database.updateGuildById(event.getGuild().getIdLong(), Updates.push("suggestion.suggestions", suggestionData)).whenComplete((result, exception) -> {
				if (ExceptionUtility.sendExceptionally(event, exception)) {
					return;
				}
				
				message.addReaction("✅").queue();
				message.addReaction("❌").queue();
				
				event.replySuccess("Your suggestion has been sent to " + channel.getAsMention()).queue();
			});
		});
	}
	
	@Command(value="remove", description="Removes a suggestion, can be your own or anyones if you have the manage server permission")
	@Examples({"suggestion remove 717843290837483611", "suggestion remove all"})
	public void remove(Sx4CommandEvent event, @Argument(value="message id") All<MessageArgument> allArgument) {
		if (allArgument.isAll()) {
			if (CheckUtility.hasPermissions(event.getMember(), event.getTextChannel(), event.getProperty("fakePermissions"), Permission.MANAGE_SERVER)) {
				event.replyFailure("You are missing the permission " + Permission.MANAGE_SERVER.getName() + " to execute this, you can remove your own suggestions only").queue();
				return;
			}
			
			event.reply(event.getAuthor().getName() + ", are you sure you want to delete **all** the suggestions in this server? (Yes or No)").queue(queryMessage -> {
				Waiter<GuildMessageReceivedEvent> waiter = new Waiter<>(GuildMessageReceivedEvent.class)
					.setPredicate(messageEvent -> messageEvent.getMessage().getContentRaw().equalsIgnoreCase("yes"))
					.setOppositeCancelPredicate()
					.setTimeout(30)
					.setUnique(event.getAuthor().getIdLong(), event.getChannel().getIdLong());
				
				waiter.onTimeout(() -> event.reply("Response timed out :stopwatch:").queue());
				
				waiter.onCancelled(type -> event.replySuccess("Cancelled").queue());
				
				waiter.future()
					.thenCompose(messageEvent -> this.database.updateGuildById(event.getGuild().getIdLong(), Updates.unset("suggestion.suggestions")))
					.whenComplete((result, exception) -> {
						if (ExceptionUtility.sendExceptionally(event, exception)) {
							return;
						}
						
						event.replySuccess("All suggestions have been deleted in this server").queue();
					});
				
				waiter.start();
			});
		} else {
			long messageId = allArgument.getValue().getMessageId();
			boolean hasPermission = CheckUtility.hasPermissions(event.getMember(), event.getTextChannel(), event.getProperty("fakePermissions"), Permission.MANAGE_SERVER);

			FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().returnDocument(ReturnDocument.BEFORE).projection(Projections.include("suggestion.suggestions"));
			
			Bson filter = Operators.eq(Operators.filter("$suggestion.suggestions", Operators.and(Operators.eq("$$this.id", messageId), Operators.or(Operators.eq("$$this.authorId", event.getAuthor().getIdLong()), hasPermission))), Collections.EMPTY_LIST);
			List<Bson> update = List.of(Operators.set("suggestion.suggestions", Operators.cond(filter, "$suggestion.suggestions", Operators.filter("$suggestion.suggestions", Operators.ne("$$this.id", messageId)))));
			this.database.findAndUpdateGuildById(event.getGuild().getIdLong(), update, options).whenComplete((data, exception) -> {
				if (ExceptionUtility.sendExceptionally(event, exception)) {
					return;
				}
				
				data = data == null ? Database.EMPTY_DOCUMENT : data;
				
				List<Document> suggestions = data.getEmbedded(List.of("suggestion", "suggestions"), Collections.emptyList());
				Document suggestion = suggestions.stream()
					.filter(suggestionData -> suggestionData.getLong("id") == messageId)
					.findFirst()
					.orElse(null);
				
				if (suggestion == null) {
					event.replyFailure("I could not find that suggestion").queue();
					return;
				}
				
				if (suggestion.getLong("authorId") != event.getAuthor().getIdLong() && !hasPermission) {
					event.replyFailure("You do not own that suggestion").queue();
					return;
				}
				
				TextChannel channel = event.getGuild().getTextChannelById(suggestion.getLong("channelId"));
				if (channel != null) {
					channel.deleteMessageById(suggestion.getLong("id")).queue(null, ErrorResponseException.ignore(ErrorResponse.UNKNOWN_MESSAGE));
				}
				
				event.replySuccess("That suggestion has been removed").queue();
			});
		}
	}
	
	@Command(value="set", description="Sets a suggestion to a specified state")
	@Examples({"suggestion set 717843290837483611 pending Need some time to think about this", "suggestion set 717843290837483611 accepted I think this is a great idea", "suggestion 717843290837483611 set denied Not possible"})
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	public void set(Sx4CommandEvent event, @Argument(value="message id") MessageArgument messageArgument, @Argument(value="state") String stateName, @Argument(value="reason", endless=true, nullDefault=true) String reason) {
		Document data = this.database.getGuildById(event.getGuild().getIdLong(), Projections.include("suggestion.states", "suggestion.suggestions", "suggestion.channelId")).get("suggestion", Database.EMPTY_DOCUMENT);
		
		List<Document> states = data.getList("states", Document.class, SuggestionState.DEFAULT_STATES);
		Document state = states.stream()
			.filter(stateData -> stateData.getString("dataName").equalsIgnoreCase(stateName))
			.findFirst()
			.orElse(null);
		
		if (state == null) {
			event.replyFailure("You do not have a suggestion state with that name").queue();
			return;
		}
		
		String stateData = state.getString("dataName");
		long messageId = messageArgument.getMessageId();
		
		List<Document> suggestions = data.getList("suggestions", Document.class, Collections.emptyList());
		Document suggestion = suggestions.stream()
			.filter(suggestionData -> suggestionData.getLong("id") == messageId)
			.findFirst()
			.orElse(null);
		
		if (suggestion == null) {
			event.replyFailure("There is no suggestion with that id").queue();
			return;
		}
		
		String reasonData = suggestion.getString("reason");
		boolean reasonMatch = reasonData == null && reason == null || (reasonData != null && reasonData.equals(reason));
		
		if (suggestion.getString("state").equals(stateData) && reasonMatch) {
			event.replyFailure("That suggestion is already in that state and has the same reason").queue();
			return;
		}
		
		TextChannel channel = event.getGuild().getTextChannelById(suggestion.getLong("channelId"));
		if (channel == null) {
			event.replyFailure("The channel for that suggestion no longer exists").queue();
			return;
		}
		
		Bson update = Updates.combine(
			reason == null ? Updates.unset("suggestion.suggestions.$[suggestion].reason") : Updates.set("suggestion.suggestions.$[suggestion].reason", reason),
			Updates.set("suggestion.suggestions.$[suggestion].state", stateData)
		);
		
		UpdateOptions options = new UpdateOptions().arrayFilters(List.of(Filters.eq("suggestion.id", messageId)));
		this.database.updateGuildById(event.getGuild().getIdLong(), update, options).whenComplete((result, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}
			
			User author = event.getShardManager().getUserById(suggestion.getLong("authorId"));
			
			channel.editMessageById(messageId, this.getSuggestionEmbed(author, event.getAuthor(), suggestion.getString("suggestion"), reason, new SuggestionState(state))).queue(message -> {
				event.replySuccess("That suggestion has been set to the `" + state.getString("name") + "` state").queue();
			});
		});
	}
	
	public class StateCommand extends Sx4Command {
		
		public StateCommand() {
			super("state");
			
			super.setDescription("Allows you to add custom states for your suggestions");
			super.setExamples("suggestion state add", "suggestion state remove");
		}
		
		public void onCommand(Sx4CommandEvent event) {
			event.replyHelp().queue();
		}
		
		@Command(value="add", description="Add a custom state to be used for suggestions")
		@Examples({"suggestion state add #FF0000 Bug", "suggestion state add #FFA500 On Hold"})
		@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
		public void add(Sx4CommandEvent event, @Argument(value="colour") @Colour int colour, @Argument(value="state name", endless=true) String stateName) {
			String dataName = stateName.toUpperCase().replace(" ", "_");
			Document stateData = new Document("name", stateName)
				.append("dataName", dataName)
				.append("colour", colour);
			
			List<Document> defaultStates = SuggestionState.getDefaultStates();
			if (defaultStates.stream().anyMatch(state -> state.getString("dataName").equals(dataName))) {
				event.replyFailure("There is already a state named that").queue();
				return;
			}
			
			defaultStates.add(stateData);
			
			List<Bson> update = List.of(Operators.set("suggestion.states", Operators.cond(Operators.and(Operators.exists("$suggestion.states"), Operators.ne(Operators.filter("$suggestion.states", Operators.eq("$$this.dataName", dataName)), Collections.EMPTY_LIST)), "$suggestion.states", Operators.cond(Operators.extinct("$suggestion.states"), defaultStates, Operators.concatArrays("$suggestion.states", List.of(stateData))))));
			this.database.updateGuildById(event.getGuild().getIdLong(), update).whenComplete((result, exception) -> {
				if (ExceptionUtility.sendExceptionally(event, exception)) {
					return;
				}
				
				if (result.getModifiedCount() == 0) {
					event.replyFailure("There is already a state named that").queue();
					return;
				}
				
				event.replySuccess("Added the suggestion state `" + dataName + "` with the colour **#" + ColourUtility.toHexString(colour) + "**").queue();
			});
		}
		
		@Command(value="remove", description="Remove a state from being used in suggestions")
		@Examples({"suggestion state remove Bug", "suggestion state remove On Hold", "suggestion state remove all"})
		@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
		public void remove(Sx4CommandEvent event, @Argument(value="state name", endless=true) All<String> allArgument) {
			if (allArgument.isAll()) {
				this.database.updateGuildById(event.getGuild().getIdLong(), Updates.unset("suggestion.states")).whenComplete((result, exception) -> {
					if (ExceptionUtility.sendExceptionally(event, exception)) {
						return;
					}
					
					if (result.getModifiedCount() == 0) {
						event.replyFailure("You already have the default states setup").queue();
						return;
					}
					
					event.replySuccess("All your suggestion states have been removed").queue();
				});
			} else {
				String dataName = allArgument.getValue().toUpperCase().replace(" ", "_");
				
				FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().returnDocument(ReturnDocument.BEFORE).projection(Projections.include("suggestion.states"));
				List<Bson> update = List.of(Operators.set("suggestion.states", Operators.cond(Operators.and(Operators.exists("$suggestion.states"), Operators.ne(Operators.size("$suggestion.states"), 1)), Operators.filter("$suggestion.states", Operators.ne("$$this.dataName", dataName)), "$suggestion.states")));
				this.database.findAndUpdateGuildById(event.getGuild().getIdLong(), update, options).whenComplete((data, exception) -> {
					if (ExceptionUtility.sendExceptionally(event, exception)) {
						return;
					}
					
					data = data == null ? Database.EMPTY_DOCUMENT : data;
					List<Document> states = data.getEmbedded(List.of("suggestion", "states"), Collections.emptyList());
					if (states.size() == 1) {
						event.replyFailure("You have to have at least 1 state at all times").queue();
						return;
					}
					
					if (states.stream().noneMatch(state -> state.getString("dataName").equals(dataName))) {
						event.replyFailure("There is no state with that name").queue();
						return;
					}
					
					event.replySuccess("Removed the suggestion state `" + dataName + "`").queue();
				});
			}
		}
		
	}
	
}
