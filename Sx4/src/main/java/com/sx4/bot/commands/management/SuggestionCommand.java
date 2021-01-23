package com.sx4.bot.commands.management;

import club.minnced.discord.webhook.WebhookClient;
import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.command.Command;
import com.mongodb.client.model.*;
import com.sx4.bot.annotations.argument.Colour;
import com.sx4.bot.annotations.argument.Options;
import com.sx4.bot.annotations.command.*;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.database.Database;
import com.sx4.bot.database.model.Operators;
import com.sx4.bot.entities.argument.Alternative;
import com.sx4.bot.entities.management.Suggestion;
import com.sx4.bot.entities.management.SuggestionState;
import com.sx4.bot.managers.SuggestionManager;
import com.sx4.bot.paged.PagedResult;
import com.sx4.bot.utility.CheckUtility;
import com.sx4.bot.utility.ColourUtility;
import com.sx4.bot.utility.ExceptionUtility;
import com.sx4.bot.waiter.Waiter;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SuggestionCommand extends Sx4Command {

	private final SuggestionManager manager = SuggestionManager.get();

	public SuggestionCommand() {
		super("suggestion", 81);
		
		super.setDescription("Create a suggestion channel where suggestions can be sent in and voted on in your server");
		super.setExamples("suggestion add", "suggestion set", "suggestion remove");
		super.setCategoryAll(ModuleCategory.MANAGEMENT);
	}

	public void onCommand(Sx4CommandEvent event) {
		event.replyHelp().queue();
	}
	
	@Command(value="toggle", description="Enables/disables suggestions in this server")
	@CommandId(82)
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
	@CommandId(83)
	@Examples({"suggestion channel", "suggestion channel #suggestions", "suggestion channel reset"})
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	public void channel(Sx4CommandEvent event, @Argument(value="channel | reset", endless=true, nullDefault=true) @Options("reset") Alternative<TextChannel> option) {
		TextChannel channel = option == null ? event.getTextChannel() : option.isAlternative() ? null : option.getValue();

		List<Bson> update = List.of(Operators.set("suggestion.channelId", channel == null ? Operators.REMOVE : channel.getIdLong()), Operators.unset("suggestion.webhook.id"), Operators.unset("suggestion.webhook.token"));
		FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().returnDocument(ReturnDocument.BEFORE).projection(Projections.include("suggestion.channelId")).upsert(true);

		this.database.findAndUpdateGuildById(event.getGuild().getIdLong(), update, options).whenComplete((data, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			long channelId = data == null ? 0L : data.getEmbedded(List.of("suggestion", "channelId"), 0L);

			if ((channel == null ? 0L : channel.getIdLong()) == channelId) {
				event.replyFailure("The suggestion channel is already " + (channel == null ? "unset" : "set to " + channel.getAsMention())).queue();
				return;
			}

			TextChannel oldChannel = channelId == 0L ? null : event.getGuild().getTextChannelById(channelId);
			if (oldChannel != null) {
				WebhookClient oldWebhook = this.manager.removeWebhook(channelId);
				if (oldWebhook != null) {
					oldChannel.deleteWebhookById(String.valueOf(oldWebhook.getId())).queue();
				}
			}
			
			event.replySuccess("The suggestion channel has been " + (channel == null ? "unset" : "set to " + channel.getAsMention())).queue();
		});
	}
	
	@Command(value="add", description="Sends a suggestion to the suggestion channel if one is setup in the server")
	@CommandId(84)
	@Redirects({"suggest"})
	@Examples({"suggestion add Add the dog emote", "suggestion Add a channel for people looking to play games"})
	@BotPermissions(permissions={Permission.MESSAGE_ADD_REACTION, Permission.MESSAGE_EMBED_LINKS})
	public void add(Sx4CommandEvent event, @Argument(value="suggestion", endless=true) String suggestion) {
		Document data = this.database.getGuildById(event.getGuild().getIdLong(), Projections.include("suggestion.channelId", "suggestion.enabled", "suggestion.webhook")).get("suggestion", Database.EMPTY_DOCUMENT);
		
		if (!data.getBoolean("enabled", false)) {
			event.replyFailure("Suggestions are not enabled in this server").queue();
			return;
		}
		
		long channelId = data.get("channelId", 0L);
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

		Suggestion suggestionData = new Suggestion(
			channelId,
			event.getGuild().getIdLong(),
			event.getAuthor().getIdLong(),
			suggestion,
			state.getDataName()
		);

		this.manager.sendSuggestion(channel, data.get("webhook", Database.EMPTY_DOCUMENT), suggestionData.getWebhookEmbed(null, event.getAuthor(), state)).whenComplete((message, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			suggestionData.setMessageId(message.getId());

			this.database.insertSuggestion(suggestionData.toData()).whenComplete((result, dataException) -> {
				if (ExceptionUtility.sendExceptionally(event, dataException)) {
					return;
				}

				channel.addReactionById(message.getId(), "✅")
					.flatMap($ -> channel.addReactionById(message.getId(), "❌"))
					.queue();

				event.replySuccess("Your suggestion has been sent to " + channel.getAsMention()).queue();
			});
		});
	}
	
	@Command(value="remove", aliases={"delete"}, description="Removes a suggestion, can be your own or anyones if you have the manage server permission")
	@CommandId(85)
	@Examples({"suggestion remove 5e45ce6d3688b30ee75201ae", "suggestion remove all"})
	public void remove(Sx4CommandEvent event, @Argument(value="id | all") @Options("all") Alternative<ObjectId> option) {
		User author = event.getAuthor();
		TextChannel channel = event.getTextChannel();

		if (option.isAlternative()) {
			if (CheckUtility.hasPermissions(event.getMember(), channel, event.getProperty("fakePermissions"), Permission.MANAGE_SERVER)) {
				event.replyFailure("You are missing the permission " + Permission.MANAGE_SERVER.getName() + " to execute this, you can remove your own suggestions only").queue();
				return;
			}
			
			event.reply(author.getName() + ", are you sure you want to delete **all** the suggestions in this server? (Yes or No)").queue(queryMessage -> {
				Waiter<GuildMessageReceivedEvent> waiter = new Waiter<>(GuildMessageReceivedEvent.class)
					.setPredicate(messageEvent -> messageEvent.getMessage().getContentRaw().equalsIgnoreCase("yes"))
					.setOppositeCancelPredicate()
					.setTimeout(30)
					.setUnique(author.getIdLong(), event.getChannel().getIdLong());
				
				waiter.onTimeout(() -> event.reply("Response timed out :stopwatch:").queue());
				
				waiter.onCancelled(type -> event.replySuccess("Cancelled").queue());
				
				waiter.future()
					.thenCompose(messageEvent -> this.database.deleteManySuggestions(Filters.eq("guildId", event.getGuild().getIdLong())))
					.whenComplete((result, exception) -> {
						if (ExceptionUtility.sendExceptionally(event, exception)) {
							return;
						}

						if (result.getDeletedCount() == 0) {
							event.replyFailure("This server has no suggestions").queue();
							return;
						}
						
						event.replySuccess("All suggestions have been deleted in this server").queue();
					});
				
				waiter.start();
			});
		} else {
			ObjectId id = option.getValue();
			boolean hasPermission = CheckUtility.hasPermissions(event.getMember(), event.getTextChannel(), event.getProperty("fakePermissions"), Permission.MANAGE_SERVER);

			Bson filter = Filters.eq("_id", id);
			if (!hasPermission) {
				filter = Filters.and(Filters.eq("authorId", author.getIdLong()), filter);
			}

			this.database.findAndDeleteSuggestion(filter).whenComplete((data, exception) -> {
				if (ExceptionUtility.sendExceptionally(event, exception)) {
					return;
				}

				if (data == null) {
					event.replyFailure("I could not find that suggestion").queue();
					return;
				}

				if (data.getLong("authorId") != author.getIdLong() && !hasPermission) {
					event.replyFailure("You do not own that suggestion").queue();
					return;
				}

				WebhookClient webhook = this.manager.getWebhook(data.getLong("channelId"));
				if (webhook != null) {
					webhook.delete(data.getLong("messageId"));
				}

				event.replySuccess("That suggestion has been removed").queue();
			});
		}
	}
	
	@Command(value="set", description="Sets a suggestion to a specified state")
	@CommandId(86)
	@Examples({"suggestion set 5e45ce6d3688b30ee75201ae pending Need some time to think about this", "suggestion set 5e45ce6d3688b30ee75201ae accepted I think this is a great idea", "suggestion 5e45ce6d3688b30ee75201ae set denied Not possible"})
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	public void set(Sx4CommandEvent event, @Argument(value="id") ObjectId id, @Argument(value="state") String stateName, @Argument(value="reason", endless=true, nullDefault=true) String reason) {
		Document data = this.database.getGuildById(event.getGuild().getIdLong(), Projections.include("suggestion.states", "suggestion.webhook")).get("suggestion", Database.EMPTY_DOCUMENT);
		
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
		
		Bson update = Updates.combine(
			reason == null ? Updates.unset("reason") : Updates.set("reason", reason),
			Updates.set("state", stateData),
			Updates.set("moderatorId", event.getAuthor().getIdLong())
		);

		FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().returnDocument(ReturnDocument.BEFORE).projection(Projections.include("channelId", "authorId", "reason", "state", "suggestion", "messageId"));
		this.database.findAndUpdateSuggestionById(id, update, options).whenComplete((suggestionData, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			if (suggestionData == null) {
				event.replyFailure("There is no suggestion with that id").queue();
				return;
			}

			Suggestion suggestion = Suggestion.fromData(suggestionData);

			String reasonData = suggestion.getReason();
			boolean reasonMatch = reasonData == null && reason == null || (reasonData != null && reasonData.equals(reason));

			if (suggestion.getState().equals(stateData) && reasonMatch) {
				event.replyFailure("That suggestion is already in that state and has the same reason").queue();
				return;
			}

			TextChannel channel = suggestion.getChannel(event.getGuild());
			if (channel == null) {
				event.replyFailure("The channel for that suggestion no longer exists").queue();
				return;
			}
			
			this.manager.editSuggestion(suggestion.getMessageId(), channel.getIdLong(), data.get("webhook", Database.EMPTY_DOCUMENT), suggestion.getWebhookEmbed(new SuggestionState(state)));

			event.replySuccess("That suggestion has been set to the `" + stateData + "` state").queue();
		});
	}

	@Command(value="view", aliases={"list"}, description="View a suggestion in the current channel")
	@CommandId(87)
	@Examples({"suggestion view 5e45ce6d3688b30ee75201ae", "suggestion view"})
	public void view(Sx4CommandEvent event, @Argument(value="id", nullDefault=true) ObjectId id) {
		Bson projection = Projections.include("suggestion", "reason", "moderatorId", "authorId", "state");
		if (id == null) {
			List<Document> suggestions = this.database.getSuggestions(Filters.eq("guildId", event.getGuild().getIdLong()), projection).into(new ArrayList<>());
			if (suggestions.isEmpty()) {
				event.replyFailure("There are not suggestions in this server").queue();
				return;
			}

			PagedResult<Document> paged = new PagedResult<>(suggestions)
				.setDisplayFunction(data -> {
					long authorId = data.getLong("authorId");
					User author = event.getShardManager().getUserById(authorId);

					return String.format("`%s` by %s - **%s**", data.getObjectId("_id").toHexString(), author == null ? authorId : author.getAsTag(), data.getString("state"));
				})
				.setIncreasedIndex(true);

			paged.onSelect(select -> {
				List<Document> states = this.database.getGuildById(event.getGuild().getIdLong(), Projections.include("suggestion.states")).getEmbedded(List.of("suggestion", "states"), SuggestionState.getDefaultStates());
				Suggestion suggestion = Suggestion.fromData(select.getSelected());

				event.reply(suggestion.getEmbed(suggestion.getFullState(states))).queue();
			});

			paged.execute(event);
		} else {
			Document suggestionData = this.database.getSuggestionById(id, projection);
			if (suggestionData == null) {
				event.replyFailure("I could not find that suggestion").queue();
				return;
			}

			List<Document> states = this.database.getGuildById(event.getGuild().getIdLong(), Projections.include("suggestion.states")).getEmbedded(List.of("suggestion", "states"), SuggestionState.getDefaultStates());
			Suggestion suggestion = Suggestion.fromData(suggestionData);

			event.reply(suggestion.getEmbed(suggestion.getFullState(states))).queue();
		}
	}
	
	public class StateCommand extends Sx4Command {
		
		public StateCommand() {
			super("state", 88);
			
			super.setDescription("Allows you to add custom states for your suggestions");
			super.setExamples("suggestion state add", "suggestion state remove");
		}
		
		public void onCommand(Sx4CommandEvent event) {
			event.replyHelp().queue();
		}
		
		@Command(value="add", description="Add a custom state to be used for suggestions")
		@CommandId(89)
		@Examples({"suggestion state add #FF0000 Bug", "suggestion state add #FFA500 On Hold"})
		@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
		public void add(Sx4CommandEvent event, @Argument(value="colour") @Colour int colour, @Argument(value="state name", endless=true) String stateName) {
			String dataName = stateName.toUpperCase().replace(" ", "_");
			Document stateData = new Document("name", stateName)
				.append("dataName", dataName)
				.append("colour", colour);
			
			List<Document> defaultStates = SuggestionState.getDefaultStates();
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
		
		@Command(value="remove", aliases={"delete"}, description="Remove a state from being used in suggestions")
		@CommandId(90)
		@Examples({"suggestion state remove Bug", "suggestion state remove On Hold", "suggestion state remove all"})
		@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
		public void remove(Sx4CommandEvent event, @Argument(value="state name | all", endless=true) @Options("all") Alternative<String> option) {
			if (option.isAlternative()) {
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
				String dataName = option.getValue().toUpperCase().replace(" ", "_");
				
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
