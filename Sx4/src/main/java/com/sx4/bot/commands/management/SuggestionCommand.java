package com.sx4.bot.commands.management;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import org.bson.Document;
import org.bson.conversions.Bson;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.command.Command;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.sx4.bot.annotations.argument.Colour;
import com.sx4.bot.annotations.command.AuthorPermissions;
import com.sx4.bot.annotations.command.BotPermissions;
import com.sx4.bot.annotations.command.Examples;
import com.sx4.bot.annotations.command.Redirects;
import com.sx4.bot.category.Category;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.database.Database;
import com.sx4.bot.database.model.Operators;
import com.sx4.bot.entities.argument.All;
import com.sx4.bot.entities.argument.MessageArgument;
import com.sx4.bot.entities.management.State;
import com.sx4.bot.utility.ColourUtility;
import com.sx4.bot.utility.ExceptionUtility;
import com.sx4.bot.waiter.Waiter;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.requests.ErrorResponse;

public class SuggestionCommand extends Sx4Command {

	public SuggestionCommand() {
		super("suggestion");
		
		super.setDescription("Create a suggestion channel where suggestions can be sent in and voted on in your server");
		super.setExamples("suggestion add", "suggestion set", "suggestion remove");
		super.setCategory(Category.MANAGEMENT);
	}
	
	private MessageEmbed getSuggestionEmbed(User author, User moderator, String suggestion, String reason, State state) {
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
		List<Bson> update = List.of(Operators.set("suggestion.enabled", Operators.cond("$suggestion.enabled", "$$REMOVE", true)));
		this.database.findAndUpdateGuildById(event.getGuild().getIdLong(), Projections.include("suggestion.enabled"), update).whenComplete((data, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}
			
			event.reply("Suggestions are now **" + (data.getEmbedded(List.of("suggestion", "enabled"), false) ? "enabled" : "disabled") + "** " + this.config.getSuccessEmote()).queue();
		});
	}
	
	@Command(value="channel", description="Sets the channel where suggestions are set to")
	@Examples({"suggestion channel", "suggestion channel #suggestions"})
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	public void channel(Sx4CommandEvent event, @Argument(value="channel", endless=true, nullDefault=true) TextChannel channel) {
		List<Bson> update = List.of(Operators.set("suggestion.channelId", channel == null ? "$$REMOVE" : channel.getIdLong()));
		this.database.updateGuildById(event.getGuild().getIdLong(), update).whenComplete((result, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}
			
			if (result.getModifiedCount() == 0) {
				event.reply("The suggestion channel is already " + (channel == null ? "unset" : "set to " + channel.getAsMention()) + " " + this.config.getFailureEmote()).queue();
				return;
			}
			
			event.reply("The suggestion channel has been " + (channel == null ? "unset" : "set to " + channel.getAsMention()) + " " + this.config.getSuccessEmote()).queue();
		});
	}
	
	@Command(value="add", description="Sends a suggestion to the suggestion channel if one is setup in the server")
	@Redirects({"suggest"})
	@Examples({"suggestion add Add the dog emote", "suggestion Add a channel for people looking to play games"})
	@BotPermissions(permissions={Permission.MESSAGE_ADD_REACTION, Permission.MESSAGE_EMBED_LINKS})
	public void add(Sx4CommandEvent event, @Argument(value="suggestion", endless=true) String suggestion) {
		Document data = this.database.getGuildById(event.getGuild().getIdLong(), Projections.include("suggestion.channelId", "suggestion.enabled")).get("suggestion", Database.EMPTY_DOCUMENT);
		
		if (!data.getBoolean("enabled", false)) {
			event.reply("Suggestions are not enabled in this server " + this.config.getFailureEmote()).queue();
			return;
		}
		
		long channelId = data.get("channelId", 0L);
		if (channelId == 0L) {
			event.reply("There is no suggestion channel " + this.config.getFailureEmote()).queue();
			return;
		}
		
		TextChannel channel = event.getGuild().getTextChannelById(channelId);
		if (channel == null) {
			event.reply("The suggestion channel no longer exists " + this.config.getFailureEmote()).queue();
			return;
		}
		
		State state = State.PENDING;
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
				
				event.reply("Your suggestion has been sent to " + channel.getAsMention() + " " + this.config.getSuccessEmote()).queue();
			});
		});
	}
	
	@Command(value="remove", description="Removes a suggestion, can be your own or anyones if you have the manage server permission")
	@Examples({"suggestion remove 717843290837483611", "suggestion remove all"})
	public void remove(Sx4CommandEvent event, @Argument(value="message id") All<MessageArgument> allArgument) {
		if (allArgument.isAll()) {
			// TODO: Use a method which includes fake permissions in the future
			if (!event.getMember().hasPermission(Permission.MANAGE_SERVER)) {
				event.reply("You are missing the permission " + Permission.MANAGE_SERVER.getName() + " to execute this, you can remove your own suggestions only " + this.config.getFailureEmote()).queue();
				return;
			}
			
			event.reply(event.getAuthor().getName() + ", are you sure you want to delete **all** the suggestions in this server? (Yes or No)").queue(queryMessage -> {
				Waiter<GuildMessageReceivedEvent> waiter = new Waiter<>(GuildMessageReceivedEvent.class)
					.setPredicate(messageEvent -> messageEvent.getAuthor().getIdLong() == event.getAuthor().getIdLong() && messageEvent.getChannel().getIdLong() == event.getChannel().getIdLong() && messageEvent.getMessage().getContentRaw().equalsIgnoreCase("yes"))
					.setCancelPredicate(messageEvent -> messageEvent.getAuthor().getIdLong() == event.getAuthor().getIdLong() && messageEvent.getChannel().getIdLong() == event.getChannel().getIdLong() && !messageEvent.getMessage().getContentRaw().equalsIgnoreCase("yes"))
					.setTimeout(30);
				
				waiter.onTimeout(() -> event.reply("Response timed out :stopwatch:").queue());
				
				waiter.onCancelled(() -> event.reply("Cancelled " + this.config.getSuccessEmote()).queue());
				
				waiter.future()
					.thenCompose(messageEvent -> this.database.updateGuildById(event.getGuild().getIdLong(), Updates.unset("suggestion.suggestions")))
					.whenComplete((result, exception) -> {
						if (ExceptionUtility.sendExceptionally(event, exception)) {
							return;
						}
						
						event.reply("All suggestions have been deleted in this server " + this.config.getSuccessEmote()).queue();
					});
				
				waiter.start();
			});
		} else {
			long messageId = allArgument.getValue().getMessageId();
			// TODO: Use a method which includes fake permissions in the future
			boolean hasPermission = event.getMember().hasPermission(Permission.MANAGE_SERVER);
			
			FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().returnDocument(ReturnDocument.BEFORE).projection(Projections.include("suggestion.suggestions"));
			
			Bson filter = Operators.eq(Operators.filter("$suggestion.suggestions", Operators.and(Operators.eq("$$this.id", messageId), Operators.or(Operators.eq("$$this.authorId", event.getAuthor().getIdLong()), hasPermission))), List.of());
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
					event.reply("I could not find that suggestion " + this.config.getFailureEmote()).queue();
					return;
				}
				
				if (suggestion.get("authorId", 0L) != event.getAuthor().getIdLong() && !hasPermission) {
					event.reply("You do not own that suggestion " + this.config.getFailureEmote()).queue();
					return;
				}
				
				TextChannel channel = event.getGuild().getTextChannelById(suggestion.get("channelId", 0L));
				if (channel != null) {
					channel.deleteMessageById(suggestion.get("id", 0L)).queue(null, new ErrorHandler().ignore(ErrorResponse.UNKNOWN_MESSAGE));
				}
				
				event.reply("That suggestion has been removed " + this.config.getSuccessEmote()).queue();
			});
		}
	}
	
	@Command(value="set", description="Sets a suggestion to a specified state")
	@Examples({"suggestion set 717843290837483611 pending Need some time to think about this", "suggestion set 717843290837483611 accepted I think this is a great idea", "suggestion 717843290837483611 set denied Not possible"})
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	public void set(Sx4CommandEvent event, @Argument(value="message id") MessageArgument messageArgument, @Argument(value="state") String stateName, @Argument(value="reason", endless=true, nullDefault=true) String reason) {
		Document data = this.database.getGuildById(event.getGuild().getIdLong(), Projections.include("suggestion.states", "suggestion.suggestions", "suggestion.channelId")).get("suggestion", Database.EMPTY_DOCUMENT);
		
		List<Document> states = data.getList("states", Document.class, State.DEFAULT_STATES);
		Document state = states.stream()
			.filter(stateData -> stateData.getString("dataName").equalsIgnoreCase(stateName))
			.findFirst()
			.orElse(null);
		
		if (state == null) {
			event.reply("You do not have a suggestion state with that name " + this.config.getFailureEmote()).queue();
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
			event.reply("There is no suggestion with that id " + this.config.getFailureEmote()).queue();
			return;
		}
		
		String reasonData = suggestion.getString("reason");
		boolean reasonMatch = reasonData == null && reason == null || (reason != null && reasonData != null && reasonData.equals(reason));
		
		if (suggestion.getString("state").equals(stateData) && reasonMatch) {
			event.reply("That suggestion is already in that state and has the same reason " + this.config.getFailureEmote()).queue();
			return;
		}
		
		TextChannel channel = event.getGuild().getTextChannelById(suggestion.get("channelId", 0L));
		if (channel == null) {
			event.reply("The channel for that suggestion no longer exists " + this.config.getFailureEmote()).queue();
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
			
			User author = event.getShardManager().getUserById(suggestion.get("authorId", 0L));
			
			channel.editMessageById(messageId, this.getSuggestionEmbed(author, event.getAuthor(), suggestion.getString("suggestion"), reason, new State(state))).queue(message -> {
				event.reply("That suggestion has been set to the `" + state.getString("name") + "` state " + this.config.getSuccessEmote()).queue();
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
			
			List<Document> defaultStates = State.getDefaultStates();
			if (defaultStates.stream().anyMatch(state -> state.getString("dataName").equals(dataName))) {
				event.reply("There is already a state named that " + this.config.getFailureEmote()).queue();
				return;
			}
			
			defaultStates.add(stateData);
			
			List<Bson> update = List.of(Operators.set("suggestion.states", Operators.cond(Operators.and(Operators.exists("$suggestion.states"), Operators.ne(Operators.filter("$suggestion.states", Operators.eq("$$this.dataName", dataName)), List.of())), "$suggestion.states", Operators.cond(Operators.extinct("$suggestion.states"), defaultStates, Operators.concatArrays("$suggestion.states", List.of(stateData))))));
			this.database.updateGuildById(event.getGuild().getIdLong(), update).whenComplete((result, exception) -> {
				if (ExceptionUtility.sendExceptionally(event, exception)) {
					return;
				}
				
				if (result.getModifiedCount() == 0) {
					event.reply("There is already a state named that " + this.config.getFailureEmote()).queue();
					return;
				}
				
				event.reply("Added the suggestion state `" + dataName + "` with the colour **#" + ColourUtility.toHexString(colour) + "** " + this.config.getSuccessEmote()).queue();
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
						event.reply("You already have the default states setup " + this.config.getFailureEmote()).queue();
						return;
					}
					
					event.reply("All your suggestion states have been removed " + this.config.getSuccessEmote()).queue();
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
						event.reply("You have to have at least 1 state at all times " + this.config.getFailureEmote()).queue();
						return;
					}
					
					if (!states.stream().anyMatch(state -> state.getString("dataName").equals(dataName))) {
						event.reply("There is no state with that name " + this.config.getFailureEmote()).queue();
						return;
					}
					
					event.reply("Removed the suggestion state `" + dataName + "` " + this.config.getSuccessEmote()).queue();
				});
			}
		}
		
	}
	
}
