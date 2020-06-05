package com.sx4.bot.commands.management;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import org.bson.Document;
import org.bson.conversions.Bson;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.command.Command;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.sx4.bot.annotations.command.AuthorPermissions;
import com.sx4.bot.annotations.command.BotPermissions;
import com.sx4.bot.annotations.command.Examples;
import com.sx4.bot.annotations.command.Redirects;
import com.sx4.bot.category.Category;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.database.Database;
import com.sx4.bot.entities.argument.MessageArgument;
import com.sx4.bot.entities.management.State;
import com.sx4.bot.utility.ExceptionUtility;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;

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
	
	@Command(value="add", description="Sends a suggestion to the suggestion channel if one is setup in the server")
	@Redirects({"suggest"})
	@Examples({"suggestion add Add the dog emote", "suggestion Add a channel for people looking to play games"})
	@BotPermissions(permissions={Permission.MESSAGE_ADD_REACTION, Permission.MESSAGE_EMBED_LINKS})
	public void add(Sx4CommandEvent event, @Argument(value="suggestion", endless=true) String suggestion) {
		Document data = this.database.getGuildById(event.getGuild().getIdLong(), Projections.include("suggestion.channelId", "suggestion.enabled")).get("suggestion", Database.EMPTY_DOCUMENT);
		
		if (!data.getBoolean("enabled", false)) {
			event.reply("Suggestions are not enabled in this server :no_entry:").queue();
			return;
		}
		
		long channelId = data.get("channelId", 0L);
		if (channelId == 0L) {
			event.reply("There is no suggestion channel :no_entry:").queue();
			return;
		}
		
		TextChannel channel = event.getGuild().getTextChannelById(channelId);
		if (channel == null) {
			event.reply("The suggestion channel no longer exists :no_entry:").queue();
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
				
				event.reply("Your suggestion has been sent to " + channel.getAsMention() + " <:done:403285928233402378>").queue();
			});
		});
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
			event.reply("You do not have a suggestion state with that name :no_entry:").queue();
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
			event.reply("There is no suggestion with that id :no_entry:").queue();
			return;
		}
		
		String reasonData = suggestion.getString("reason");
		boolean reasonMatch = reasonData == null && reason == null || (reason != null && reasonData != null && reasonData.equals(reason));
		
		if (suggestion.getString("state").equals(stateData) && reasonMatch) {
			event.reply("That suggestion is already in that state and has the same reason :no_entry:").queue();
			return;
		}
		
		TextChannel channel = event.getGuild().getTextChannelById(suggestion.get("channelId", 0L));
		if (channel == null) {
			event.reply("The channel for that suggestion no longer exists :no_entry:").queue();
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
				event.reply("That suggestion has been set to the `" + state.getString("name") + "` state <:done:403285928233402378>").queue();
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
		
	}
	
}
