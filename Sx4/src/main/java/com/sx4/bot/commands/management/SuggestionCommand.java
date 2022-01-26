package com.sx4.bot.commands.management;

import club.minnced.discord.webhook.WebhookClient;
import club.minnced.discord.webhook.send.WebhookEmbed;
import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.command.Command;
import com.mongodb.client.model.*;
import com.sx4.bot.annotations.argument.AlternativeOptions;
import com.sx4.bot.annotations.argument.Colour;
import com.sx4.bot.annotations.argument.ImageUrl;
import com.sx4.bot.annotations.command.*;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.database.mongo.MongoDatabase;
import com.sx4.bot.database.mongo.model.Operators;
import com.sx4.bot.entities.argument.Alternative;
import com.sx4.bot.entities.argument.MessageArgument;
import com.sx4.bot.entities.argument.Or;
import com.sx4.bot.entities.management.Suggestion;
import com.sx4.bot.entities.management.SuggestionState;
import com.sx4.bot.paged.PagedResult;
import com.sx4.bot.utility.ButtonUtility;
import com.sx4.bot.utility.ColourUtility;
import com.sx4.bot.utility.ExceptionUtility;
import com.sx4.bot.waiter.Waiter;
import com.sx4.bot.waiter.exception.CancelException;
import com.sx4.bot.waiter.exception.TimeoutException;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.interactions.components.Button;
import net.dv8tion.jda.api.requests.ErrorResponse;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.time.Clock;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletionException;

public class SuggestionCommand extends Sx4Command {

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
		event.getMongo().findAndUpdateGuildById(event.getGuild().getIdLong(), Projections.include("suggestion.enabled"), update).whenComplete((data, exception) -> {
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
	public void channel(Sx4CommandEvent event, @Argument(value="channel | reset", endless=true, nullDefault=true) @AlternativeOptions("reset") Alternative<TextChannel> option) {
		TextChannel channel = option == null ? event.getTextChannel() : option.isAlternative() ? null : option.getValue();

		List<Bson> update = List.of(Operators.set("suggestion.channelId", channel == null ? Operators.REMOVE : channel.getIdLong()), Operators.unset("suggestion.webhook.id"), Operators.unset("suggestion.webhook.token"));
		FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().returnDocument(ReturnDocument.BEFORE).projection(Projections.include("suggestion.webhook.id", "suggestion.channelId")).upsert(true);

		event.getMongo().findAndUpdateGuildById(event.getGuild().getIdLong(), update, options).whenComplete((data, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			long channelId = data == null ? 0L : data.getEmbedded(List.of("suggestion", "channelId"), 0L);
			event.getBot().getSuggestionManager().removeWebhook(channelId);

			if ((channel == null ? 0L : channel.getIdLong()) == channelId) {
				event.replyFailure("The suggestion channel is already " + (channel == null ? "unset" : "set to " + channel.getAsMention())).queue();
				return;
			}

			TextChannel oldChannel = channelId == 0L ? null : event.getGuild().getTextChannelById(channelId);
			long webhookId = data == null ? 0L : data.getEmbedded(List.of("suggestion", "webhook", "id"), 0L);

			if (oldChannel != null && webhookId != 0L) {
				oldChannel.deleteWebhookById(Long.toString(webhookId)).queue(null, ErrorResponseException.ignore(ErrorResponse.UNKNOWN_WEBHOOK));
			}
			
			event.replySuccess("The suggestion channel has been " + (channel == null ? "unset" : "set to " + channel.getAsMention())).queue();
		});
	}
	
	@Command(value="add", description="Sends a suggestion to the suggestion channel if one is setup in the server")
	@CommandId(84)
	@Redirects({"suggest"})
	@Examples({"suggestion add Add the dog emote", "suggestion Add a channel for people looking to play games"})
	@BotPermissions(permissions={Permission.MESSAGE_ADD_REACTION, Permission.MESSAGE_EMBED_LINKS})
	public void add(Sx4CommandEvent event, @Argument(value="suggestion", endless=true) String description) {
		Document data = event.getMongo().getGuildById(event.getGuild().getIdLong(), Projections.include("suggestion.channelId", "suggestion.enabled", "suggestion.webhook", "premium.endAt"));

		Document suggestionData = data.get("suggestion", MongoDatabase.EMPTY_DOCUMENT);
		if (!suggestionData.getBoolean("enabled", false)) {
			event.replyFailure("Suggestions are not enabled in this server").queue();
			return;
		}
		
		long channelId = suggestionData.get("channelId", 0L);
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

		String image = event.getMessage().getAttachments().stream()
			.filter(Message.Attachment::isImage)
			.map(Message.Attachment::getUrl)
			.findFirst()
			.orElse(null);

		Suggestion suggestion = new Suggestion(
			channelId,
			event.getGuild().getIdLong(),
			event.getAuthor().getIdLong(),
			description,
			image,
			state.getDataName()
		);

		boolean premium = Clock.systemUTC().instant().getEpochSecond() < data.getEmbedded(List.of("premium", "endAt"), 0L);

		event.getBot().getSuggestionManager().sendSuggestion(channel, suggestionData.get("webhook", MongoDatabase.EMPTY_DOCUMENT), premium, suggestion.getWebhookEmbed(null, event.getAuthor(), state)).whenComplete((message, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			suggestion.setMessageId(message.getId());

			event.getMongo().insertSuggestion(suggestion.toData()).whenComplete((result, dataException) -> {
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
	public void remove(Sx4CommandEvent event, @Argument(value="id | message | all", acceptEmpty=true) @AlternativeOptions("all") Alternative<Or<MessageArgument, ObjectId>> option) {
		User author = event.getAuthor();

		if (option.isAlternative()) {
			if (!event.hasPermission(event.getMember(), Permission.MANAGE_SERVER)) {
				event.replyFailure("You are missing the permission " + Permission.MANAGE_SERVER.getName() + " to execute this, you can remove your own suggestions only").queue();
				return;
			}

			List<Button> buttons = List.of(Button.success("yes", "Yes"), Button.danger("no", "No"));
			
			event.reply(author.getName() + ", are you sure you want to delete **all** the suggestions in this server?").setActionRow(buttons).submit().thenCompose(message -> {
				return new Waiter<>(event.getBot(), ButtonClickEvent.class)
					.setPredicate(e -> ButtonUtility.handleButtonConfirmation(e, message, event.getAuthor()))
					.setCancelPredicate(e -> ButtonUtility.handleButtonCancellation(e, message, event.getAuthor()))
					.onFailure(e -> ButtonUtility.handleButtonFailure(e, message))
					.setTimeout(60)
					.start();
			}).whenComplete((e, exception) -> {
				Throwable cause = exception instanceof CompletionException ? exception.getCause() : exception;
				if (cause instanceof CancelException) {
					GenericEvent cancelEvent = ((CancelException) cause).getEvent();
					if (cancelEvent != null) {
						((ButtonClickEvent) cancelEvent).reply("Cancelled " + event.getConfig().getSuccessEmote()).queue();
					}

					return;
				} else if (cause instanceof TimeoutException) {
					event.reply("Timed out :stopwatch:").queue();
					return;
				} else if (ExceptionUtility.sendExceptionally(event, exception)) {
					return;
				}

				event.getMongo().deleteManySuggestions(Filters.eq("guildId", event.getGuild().getIdLong())).whenComplete((result, databaseException) -> {
					if (ExceptionUtility.sendExceptionally(event, databaseException)) {
						return;
					}

					if (result.getDeletedCount() == 0) {
						e.reply("This server has no suggestions " + event.getConfig().getFailureEmote()).queue();
						return;
					}

					e.reply("All suggestions have been deleted in this server " + event.getConfig().getSuccessEmote()).queue();
				});
			});
		} else {
			Or<MessageArgument, ObjectId> argument = option.getValue();
			boolean hasPermission = event.hasPermission(event.getMember(), Permission.MANAGE_SERVER);

			Bson filter = Filters.and(argument.hasFirst() ? Filters.eq("messageId", argument.getFirst().getMessageId()) : Filters.eq("_id", argument.getSecond()), Filters.eq("guildId", event.getGuild().getIdLong()));
			if (!hasPermission) {
				filter = Filters.and(Filters.eq("authorId", author.getIdLong()), filter);
			}

			event.getMongo().findAndDeleteSuggestion(filter).whenComplete((data, exception) -> {
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

				WebhookClient webhook = event.getBot().getSuggestionManager().getWebhook(data.getLong("channelId"));
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
	public void set(Sx4CommandEvent event, @Argument(value="id | message", acceptEmpty=true) Or<ObjectId, MessageArgument> argument, @Argument(value="state") String stateName, @Argument(value="reason", endless=true, nullDefault=true) String reason) {
		Document data = event.getMongo().getGuildById(event.getGuild().getIdLong(), Projections.include("suggestion.states", "suggestion.webhook")).get("suggestion", MongoDatabase.EMPTY_DOCUMENT);

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

		Bson filter = Filters.and(argument.hasFirst() ? Filters.eq("_id", argument.getFirst()) : Filters.eq("messageId", argument.getSecond().getMessageId()), Filters.eq("guildId", event.getGuild().getIdLong()));

		FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().returnDocument(ReturnDocument.BEFORE).projection(Projections.include("channelId", "authorId", "reason", "state", "suggestion", "messageId", "image"));
		event.getMongo().findAndUpdateSuggestion(filter, update, options).whenComplete((suggestionData, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			if (suggestionData == null) {
				event.replyFailure("There is no suggestion with that id").queue();
				return;
			}

			String reasonData = suggestionData.getString("reason");

			boolean reasonMatch = reasonData == null && reason == null || (reasonData != null && reasonData.equals(reason));
			if (suggestionData.getString("state").equals(stateData) && reasonMatch) {
				event.replyFailure("That suggestion is already in that state and has the same reason").queue();
				return;
			}

			TextChannel channel = event.getGuild().getTextChannelById(suggestionData.getLong("channelId"));
			if (channel == null) {
				event.replyFailure("The channel for that suggestion no longer exists").queue();
				return;
			}

			User author = event.getShardManager().getUserById(suggestionData.getLong("authorId"));

			long messageId = suggestionData.getLong("messageId");
			if (author != null) {
				author.openPrivateChannel()
					.flatMap(privateChannel -> privateChannel.sendMessage("Your suggestion has been updated by a moderator, click the message link to view it\nhttps://discord.com/channels/" + event.getGuild().getIdLong() + "/" + channel.getIdLong() + "/" + messageId))
					.queue(null, ErrorResponseException.ignore(ErrorResponse.CANNOT_SEND_TO_USER));
			}

			WebhookEmbed embed = Suggestion.getWebhookEmbed(suggestionData.getObjectId("_id"), event.getAuthor(), author, suggestionData.getString("suggestion"), suggestionData.getString("image"), reason, new SuggestionState(state));

			event.getBot().getSuggestionManager().editSuggestion(messageId, channel.getIdLong(), data.get("webhook", MongoDatabase.EMPTY_DOCUMENT), embed);

			event.replySuccess("That suggestion has been set to the `" + stateData + "` state").queue();
		});
	}

	@Command(value="view", aliases={"list"}, description="View a suggestion in the current channel")
	@CommandId(87)
	@Examples({"suggestion view 5e45ce6d3688b30ee75201ae", "suggestion view"})
	public void view(Sx4CommandEvent event, @Argument(value="id | message", nullDefault=true, acceptEmpty=true) Or<MessageArgument, ObjectId> argument) {
		Bson filter = Filters.eq("guildId", event.getGuild().getIdLong());
		if (argument != null) {
			filter = Filters.and(filter, argument.hasFirst() ? Filters.eq("messageId", argument.getFirst().getMessageId()) : Filters.eq("_id", argument.getSecond()));
		}

		List<Bson> guildPipeline = List.of(
			Aggregates.project(Projections.computed("states", Operators.ifNull("$suggestion.states", SuggestionState.getDefaultStates()))),
			Aggregates.match(Filters.eq("_id", event.getGuild().getIdLong()))
		);

		List<Bson> pipeline = List.of(
			Aggregates.match(filter),
			Aggregates.group(null, Accumulators.push("suggestions", Operators.ROOT)),
			Aggregates.unionWith("guilds", guildPipeline),
			Aggregates.group(null, Accumulators.max("states", "$states"), Accumulators.max("suggestions", Operators.ifNull("$suggestions", Collections.EMPTY_LIST)))
		);

		event.getMongo().aggregateSuggestions(pipeline).whenComplete((documents, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			Document data = documents.isEmpty() ? MongoDatabase.EMPTY_DOCUMENT : documents.get(0);

			List<Document> suggestions = data.getList("suggestions", Document.class);
			if (suggestions.isEmpty()) {
				event.replyFailure("There are no suggestions in this server").queue();
				return;
			}

			List<Document> states = data.getList("states", Document.class);

			PagedResult<Document> paged = new PagedResult<>(event.getBot(), suggestions)
				.setIncreasedIndex(true)
				.setDisplayFunction(d -> {
					long authorId = d.getLong("authorId");
					User author = event.getShardManager().getUserById(authorId);

					return String.format("`%s` by %s - **%s**", d.getObjectId("_id").toHexString(), author == null ? authorId : author.getAsTag(), d.getString("state"));
				});

			paged.onSelect(select -> {
				Suggestion suggestion = Suggestion.fromData(select.getSelected());

				event.reply(suggestion.getEmbed(event.getShardManager(), suggestion.getFullState(states))).queue();
			});

			paged.execute(event);
		});

	}

	@Command(value="name", description="Set the name of the webhook that sends suggestion messages")
	@CommandId(437)
	@Examples({"suggestion name Suggestions", "suggestion name Server Suggestions"})
	@Premium
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	public void name(Sx4CommandEvent event, @Argument(value="name", endless=true) String name) {
		event.getMongo().updateGuildById(event.getGuild().getIdLong(), Updates.set("suggestion.webhook.name", name)).whenComplete((result, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			if (result.getModifiedCount() == 0 && result.getUpsertedId() == null) {
				event.replyFailure("Your suggestion webhook name was already set to that").queue();
				return;
			}

			event.replySuccess("Your suggestion webhook name has been updated, this only works with premium <https://patreon.com/Sx4>").queue();
		});
	}

	@Command(value="avatar", description="Set the avatar of the webhook that sends suggestion messages")
	@CommandId(438)
	@Examples({"suggestion avatar Shea#6653", "suggestion avatar https://i.imgur.com/i87lyNO.png"})
	@Premium
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	public void avatar(Sx4CommandEvent event, @Argument(value="avatar", endless=true, acceptEmpty=true) @ImageUrl String url) {
		event.getMongo().updateGuildById(event.getGuild().getIdLong(), Updates.set("suggestion.webhook.avatar", url)).whenComplete((result, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			if (result.getModifiedCount() == 0 && result.getUpsertedId() == null) {
				event.replyFailure("Your suggestion webhook avatar was already set to that").queue();
				return;
			}

			event.replySuccess("Your suggestion webhook avatar has been updated, this only works with premium <https://patreon.com/Sx4>").queue();
		});
	}
	
	public static class StateCommand extends Sx4Command {
		
		public StateCommand() {
			super("state", 88);
			
			super.setDescription("Allows you to add custom states for your suggestions");
			super.setExamples("suggestion state add", "suggestion state remove");
			super.setCategoryAll(ModuleCategory.MANAGEMENT);
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
			event.getMongo().updateGuildById(event.getGuild().getIdLong(), update).whenComplete((result, exception) -> {
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
		public void remove(Sx4CommandEvent event, @Argument(value="state name | all", endless=true) @AlternativeOptions("all") Alternative<String> option) {
			if (option.isAlternative()) {
				event.getMongo().updateGuildById(event.getGuild().getIdLong(), Updates.unset("suggestion.states")).whenComplete((result, exception) -> {
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
				event.getMongo().findAndUpdateGuildById(event.getGuild().getIdLong(), update, options).whenComplete((data, exception) -> {
					if (ExceptionUtility.sendExceptionally(event, exception)) {
						return;
					}
					
					data = data == null ? MongoDatabase.EMPTY_DOCUMENT : data;
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
