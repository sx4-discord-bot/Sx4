package com.sx4.bot.commands.management;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.command.Command;
import com.jockie.bot.core.command.Command.Cooldown;
import com.mongodb.MongoWriteException;
import com.mongodb.client.model.*;
import com.sx4.bot.annotations.argument.Limit;
import com.sx4.bot.annotations.argument.Options;
import com.sx4.bot.annotations.command.AuthorPermissions;
import com.sx4.bot.annotations.command.BotPermissions;
import com.sx4.bot.annotations.command.CommandId;
import com.sx4.bot.annotations.command.Examples;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.database.model.Operators;
import com.sx4.bot.entities.argument.Alternative;
import com.sx4.bot.entities.argument.MessageArgument;
import com.sx4.bot.entities.settings.HolderType;
import com.sx4.bot.utility.ExceptionUtility;
import com.sx4.bot.waiter.Waiter;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.IPermissionHolder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageReaction.ReactionEmote;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

public class ReactionRoleCommand extends Sx4Command {

	private final Predicate<Throwable> defaultReactionFailure = exception -> {
		if (exception instanceof ErrorResponseException) {
			ErrorResponseException errorResponse = ((ErrorResponseException) exception);
			return errorResponse.getErrorCode() == 400 || errorResponse.getErrorResponse() == ErrorResponse.UNKNOWN_EMOJI;
		}
		
		return false;
	};

	private static Predicate<Document> getReactionFilter(ReactionEmote emote) {
		return data -> {
			Document emoteData = data.get("emote", Document.class);
			if (emote.isEmoji()) {
				if (emoteData.containsKey("name")) {
					return emoteData.getString("name").equals(emote.getEmoji());
				}

				return false;
			} else {
				return emoteData.getLong("id") == emote.getEmote().getIdLong();
			}
		};
	}
	
	public ReactionRoleCommand() {
		super("reaction role", 71);
		
		super.setDescription("Set up a reaction role so users can simply react to an emote and get a specified role");
		super.setAliases("reactionrole");
		super.setExamples("reaction role add", "reaction role remove");
		super.setCategoryAll(ModuleCategory.MANAGEMENT);
	}
	
	public void onCommand(Sx4CommandEvent event) {
		event.replyHelp().queue();
	}
	
	@Command(value="add", description="Adds a role to be given when a user reacts to the specified emote")
	@CommandId(72)
	@Examples({"reaction role add 643945552865919002 🐝 @Yellow", "reaction role add https://discordapp.com/channels/330399610273136641/678274453158887446/680051429460803622 :doggo: Dog person"})
	@AuthorPermissions(permissions={Permission.MANAGE_ROLES})
	@BotPermissions(permissions={Permission.MESSAGE_ADD_REACTION, Permission.MESSAGE_HISTORY})
	@Cooldown(value=2)
	public void add(Sx4CommandEvent event, @Argument(value="message id") MessageArgument messageArgument, @Argument(value="emote") ReactionEmote emote, @Argument(value="role", endless=true) Role role) {
		if (role.isPublicRole()) {
			event.replyFailure("I cannot give the @everyone role").queue();
			return;
		}
		
		if (role.isManaged()) {
			event.replyFailure("I cannot give managed roles").queue();
			return;
		}
		
		if (!event.getSelfMember().canInteract(role)) {
			event.replyFailure("I cannot give a role higher or equal than my top role").queue();
			return;
		}
		
		if (!event.getMember().canInteract(role)) {
			event.replyFailure("You cannot give a role higher or equal than your top role").queue();
			return;
		}
		
		boolean unicode = emote.isEmoji();
		String identifier = unicode ? "name" : "id";
		messageArgument.retrieveMessage().queue(message -> {
			if (message.getReactions().size() >= 20) {
				event.replyFailure("That message is at the max amount of reactions (20)").queue();
				return;
			}

			Document reactionData = new Document("emote", new Document(identifier, unicode ? emote.getEmoji() : emote.getEmote().getIdLong()))
				.append("roles", List.of(role.getIdLong()));

			Object emoteStore = unicode ? emote.getEmoji() : emote.getEmote().getIdLong();

			Bson reactionMap = Operators.ifNull("$reactions", Collections.EMPTY_LIST);
			Bson reactionFilter = Operators.filter(reactionMap, Operators.eq("$$this.emote." + identifier, emoteStore));
			Bson roleMap = Operators.ifNull(Operators.first(Operators.map(reactionFilter, "$$this.roles")), Collections.EMPTY_LIST);

			Bson reactions = Operators.cond(Operators.isEmpty(reactionFilter), Operators.concatArrays(List.of(reactionData), Operators.filter(reactionMap, Operators.ne("$$this.emote." + identifier, emoteStore))), Operators.concatArrays(List.of(Operators.mergeObjects(Operators.first(reactionFilter), new Document("roles", Operators.concatArrays(Operators.filter(roleMap, Operators.ne("$$this", role.getIdLong())), List.of(role.getIdLong()))))), Operators.filter(reactionMap, Operators.ne("$$this.emote." + identifier, emoteStore))));
			List<Bson> update = List.of(Operators.set("reactions", reactions), Operators.set("guildId", event.getGuild().getIdLong()));

			if (unicode && message.getReactionByUnicode(emote.getEmoji()) == null) {
				message.addReaction(emote.getEmoji()).queue($ -> {
					event.getDatabase().updateReactionRole(Filters.eq("messageId", message.getIdLong()), update).whenComplete((result, exception) -> {
						if (ExceptionUtility.sendExceptionally(event, exception)) {
							return;
						}

						if (result.getModifiedCount() == 0 && result.getUpsertedId() == null) {
							event.replyFailure("That role is already given when reacting to this reaction").queue();
							return;
						}
						
						event.replySuccess("The role " + role.getAsMention() + " will now be given when reacting to " + emote.getEmoji()).queue();
					});
				}, new ErrorHandler().handle(this.defaultReactionFailure, exception -> event.replyFailure("I could not find that emote").queue()));
			} else {
				if (!unicode && message.getReactionById(emote.getEmote().getIdLong()) == null) {
					message.addReaction(emote.getEmote()).queue();
				}

				event.getDatabase().updateReactionRole(Filters.eq("messageId", message.getIdLong()), update).whenComplete((result, exception) -> {
					if (ExceptionUtility.sendExceptionally(event, exception)) {
						return;
					}

					if (result.getModifiedCount() == 0 && result.getUpsertedId() == null) {
						event.replyFailure("That role is already given when reacting to this reaction").queue();
						return;
					}
					
					event.replySuccess("The role " + role.getAsMention() + " will now be given when reacting to " + (unicode ? emote.getEmoji() : emote.getEmote().getAsMention())).queue();
				});
			}
		}, new ErrorHandler().handle(ErrorResponse.UNKNOWN_MESSAGE, exception -> event.replyFailure("I could not find that message").queue()));
	}
	
	@Command(value="remove", description="Removes a role or a whole reaction from the reaction role")
	@CommandId(73)
	@Examples({"reaction role remove 643945552865919002 🐝", "reaction role remove https://discordapp.com/channels/330399610273136641/678274453158887446/680051429460803622 🐝 @Yellow"})
	@AuthorPermissions(permissions={Permission.MANAGE_ROLES})
	@Cooldown(value=2)
	public void remove(Sx4CommandEvent event, @Argument(value="message id") MessageArgument messageArgument, @Argument(value="emote") ReactionEmote emote, @Argument(value="role", endless=true, nullDefault=true) Role role) {
		boolean unicode = emote.isEmoji();
		String identifier = unicode ? "name" : "id";
		
		Bson update;
		List<Bson> arrayFilters = null;
		if (role == null) {
			update = Updates.pull("reactions", Filters.eq("emote." + identifier, unicode ? emote.getEmoji() : emote.getEmote().getIdLong()));
		} else {
			update = Updates.pull("reactions.$[reaction].roles", role.getIdLong());
			arrayFilters = List.of(Filters.eq("reaction.emote." + identifier, unicode ? emote.getEmoji() : emote.getEmote().getIdLong()));
		}
		
		FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().arrayFilters(arrayFilters).returnDocument(ReturnDocument.BEFORE);
		event.getDatabase().findAndUpdateReactionRole(Filters.eq("messageId", messageArgument.getMessageId()), update, options).whenComplete((data, exception) -> {
			Throwable cause = exception == null ? null : exception.getCause();
			if (cause instanceof MongoWriteException && ((MongoWriteException) cause).getCode() == 2) {
				event.replyFailure("There was no reaction role on that message").queue();
				return;
			}
				
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}
			
			if (data == null) {
				event.replyFailure("There was no reaction role on that message").queue();
				return;
			}
			
			Document reaction = data.getList("reactions", Document.class).stream()
				.filter(ReactionRoleCommand.getReactionFilter(emote))
				.findFirst()
				.orElse(null);
			
			if (reaction == null) {
				event.replyFailure("There was no reaction role for that emote").queue();
				return;
			}
			
			if (role == null) {
				event.replySuccess("The reaction " + (unicode ? emote.getEmoji() : emote.getEmote().getAsMention()) + " will no longer give any roles").queue();
			} else {
				if (!reaction.getList("roles", Long.class).contains(role.getIdLong())) {
					event.replyFailure("That role is not given when reacting to that emote").queue();
					return;
				}
			
				event.replySuccess("The role " + role.getAsMention() + " has been removed from that reaction").queue();
			}
		});
	}
	
	@Command(value="dm", description="Enables/disables whether a reaction role should send dms when a user acquires roles")
	@CommandId(74)
	@Examples({"reaction role dm enable all", "reaction role dm disable all", "reaction role dm enable 643945552865919002", "reaction role dm disable 643945552865919002"})
	@AuthorPermissions(permissions={Permission.MANAGE_ROLES})
	@Cooldown(value=2)
	public void dm(Sx4CommandEvent event, @Argument(value="update type") @Options({"enable", "disable"}) String value, @Argument(value="message id | all") @Options("all") Alternative<MessageArgument> option) {
		boolean alternative = option.isAlternative(), enable = value.equals("enable");
		long messageId = alternative ? 0L : option.getValue().getMessageId();

		Bson filter = alternative ? Filters.eq("guildId", event.getGuild().getIdLong()) : Filters.eq("messageId", messageId);
		Bson update = enable ? Updates.unset("dm") : Updates.set("dm", false);

		event.getDatabase().updateManyReactionRoles(filter, update).whenComplete((result, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}
			
			if (result.getModifiedCount() == 0 && result.getMatchedCount() != 0) {
				event.replyFailure((alternative ? "All your reaction roles" : "That reaction role") + " already " + (alternative ? "have" : "has") + " it set to " + (enable ? "" : "not ") + "dm users").queue();
				return;
			}
			
			if (result.getModifiedCount() == 0) {
				event.replyFailure(alternative ? "You do not have any reaction roles setup " + event.getConfig().getFailureEmote() : "There was no reaction role on that message").queue();
				return;
			}
			
			event.replySuccess((alternative ? "All your reaction roles" : "That reaction role") + " will " + (enable ? "now" : "no longer") + " send dms").queue();
		});
	}
	
	@Command(value="max reactions", aliases={"maxreactions"}, description="Sets the max amount of reactions a user can react to simultanously")
	@CommandId(75)
	@Examples({"reaction role max reactions all 2", "reaction role max reactions 643945552865919002 0"})
	@AuthorPermissions(permissions={Permission.MANAGE_ROLES})
	@Cooldown(value=2)
	public void maxReactions(Sx4CommandEvent event, @Argument(value="message id | all") @Options("all") Alternative<MessageArgument> option, @Argument(value="max reactions") @Limit(min=0, max=20) int maxReactions) {
		boolean alternative = option.isAlternative(), unlimited = maxReactions == 0;
		long messageId = alternative ? 0L : option.getValue().getMessageId();

		Bson filter = alternative ? Filters.eq("guildId", event.getGuild().getIdLong()) : Filters.eq("messageId", messageId);
		Bson update = unlimited ? Updates.unset("maxReactions") : Updates.set("maxReactions", maxReactions);

		event.getDatabase().updateManyReactionRoles(filter, update).whenComplete((result, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}
			
			if (result.getModifiedCount() == 0 && result.getMatchedCount() != 0) {
				event.reply((alternative ? "All your reaction roles" : "That reaction role") + " already " + (alternative ? "have" : "has") + " it set to " + (unlimited ? "unlimted" : "**" + maxReactions + "**") + " max reaction" + (maxReactions == 1 ? "" : "s") + event.getConfig().getFailureEmote()).queue();
				return;
			}
			
			if (result.getModifiedCount() == 0) {
				event.replyFailure(alternative ? "You do not have any reaction roles setup " + event.getConfig().getFailureEmote() : "There was no reaction role on that message").queue();
				return;
			}
			
			event.replySuccess((alternative ? "All your reaction roles" : "That reaction role") + " now " + (alternative ? "have " : "has ") + (unlimited ? "no cap for" : "a cap of **" + maxReactions + "**") + " reaction" + (maxReactions == 1 ? "" : "s")).queue();
		});
	}
	
	@Command(value="delete", aliases={"remove"}, description="Deletes a reaction role")
	@CommandId(76)
	@Examples({"reaction role delete 643945552865919002", "reaction role delete all"})
	@AuthorPermissions(permissions={Permission.MANAGE_ROLES})
	@Cooldown(value=5)
	public void delete(Sx4CommandEvent event, @Argument(value="message id | all") @Options("all") Alternative<MessageArgument> option) {
		if (option.isAlternative()) {
			event.reply(event.getAuthor().getName() + ", are you sure you want to delete **all** the reaction roles in this server? (Yes or No)").submit()
				.thenCompose(message -> {
					Waiter<GuildMessageReceivedEvent> waiter = new Waiter<>(event.getBot(), GuildMessageReceivedEvent.class)
						.setPredicate(messageEvent -> messageEvent.getMessage().getContentRaw().equalsIgnoreCase("yes"))
						.setOppositeCancelPredicate()
						.setTimeout(30)
						.setUnique(event.getAuthor().getIdLong(), event.getChannel().getIdLong());
					
					waiter.onTimeout(() -> event.reply("Response timed out :stopwatch:").queue());
					
					waiter.onCancelled(type -> event.replySuccess("Cancelled").queue());
					
					waiter.start();
					
					return waiter.future();
				})
				.thenCompose(messageEvent -> event.getDatabase().deleteManyReactionRoles(Filters.eq("guildId", event.getGuild().getIdLong())))
				.whenComplete((result, exception) -> {
					if (ExceptionUtility.sendExceptionally(event, exception)) {
						return;
					}

					if (result.getDeletedCount() == 0) {
						event.replyFailure("There are no reaction roles in this server").queue();
						return;
					}
					
					event.replySuccess("All reaction role data has been deleted in this server").queue();
				});
		} else {
			long messageId = option.getValue().getMessageId();
			event.getDatabase().deleteReactionRole(Filters.eq("messageId", messageId)).whenComplete((result, exception) -> {
				if (ExceptionUtility.sendExceptionally(event, exception)) {
					return;
				}
				
				if (result.getDeletedCount() == 0) {
					event.replyFailure("There was no reaction role on that message").queue();
					return;
				}
				
				event.replySuccess("That reaction role has been deleted").queue();
			});
		}
	}

	public static class WhitelistCommand extends Sx4Command {

		public WhitelistCommand() {
			super("whitelist", 77);

			super.setDescription("Whitelists a user or role from being able to use the reaction on the reaction role");
			super.setExamples("reaction role whitelist add", "reaction role whitelist remove", "reaction role whitelist list");
		}

		public void onCommand(Sx4CommandEvent event) {
			event.replyHelp().queue();
		}

		@Command(value="add", description="Adds a whitelist for a user or role to be able to use a reaction on a reaction role")
		@CommandId(78)
		@Examples({"reaction role whitelist add 643945552865919002 🐝 @Shea#6653", "reaction role whitelist add 643945552865919002 :doggo: @Role", "reaction role whitelist add 643945552865919002 @Role"})
		@AuthorPermissions(permissions={Permission.MANAGE_ROLES})
		public void add(Sx4CommandEvent event, @Argument(value="message id") MessageArgument messageArgument, @Argument(value="emote", nullDefault=true) ReactionEmote emote, @Argument(value="user | role", endless=true)IPermissionHolder holder) {
			boolean role = holder instanceof Role;

			Document holderData = new Document("id", holder.getIdLong())
				.append("type", role ? HolderType.ROLE.getType() : HolderType.USER.getType());

			Bson reactions = Operators.ifNull("$reactions", Collections.EMPTY_LIST);

			List<Bson> update;
			if (emote != null) {
				boolean emoji = emote.isEmoji();

				Bson reaction = Operators.filter(reactions, Operators.eq("$$this.emote." + (emoji ? "name" : "id"), emoji ? emote.getEmoji() : emote.getEmote().getIdLong()));
				Bson permissionsMap = Operators.ifNull(Operators.first(Operators.map(reaction, "$$this.permissions")), Collections.EMPTY_LIST);
				Bson holderFilter = Operators.filter(permissionsMap, Operators.eq("$$this.id", holder.getIdLong()));
				Bson holderMap = Operators.ifNull(Operators.first(holderFilter), holderData);

				Bson result = Operators.concatArrays(List.of(Operators.mergeObjects(Operators.first(reaction), new Document("permissions", Operators.concatArrays(Operators.filter(permissionsMap, Operators.ne("$$this.id", holder.getIdLong())), List.of(Operators.mergeObjects(holderMap, new Document("granted", true))))))), Operators.filter(reactions, Operators.ne("$$this.emote." + (emoji ? "name" : "id"), emoji ? emote.getEmoji() : emote.getEmote().getIdLong())));
				update = List.of(Operators.set("reactions", result));
			} else {
				Bson result = Operators.map(reactions, Operators.mergeObjects("$$this", new Document("permissions", Operators.concatArrays(Operators.filter(Operators.ifNull("$$this.permissions", Collections.EMPTY_LIST), Operators.ne("$$this.id", holder.getIdLong())), List.of(Operators.mergeObjects(Operators.ifNull(Operators.first(Operators.filter(Operators.ifNull("$$this.permissions", Collections.EMPTY_LIST), Operators.eq("$$this.id", holder.getIdLong()))), holderData), new Document("granted", true)))))));
				update = List.of(Operators.set("reactions", result));
			}

			FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().projection(Projections.include("reactions")).returnDocument(ReturnDocument.BEFORE);
			event.getDatabase().findAndUpdateReactionRole(Filters.eq("messageId", messageArgument.getMessageId()), update, options).whenComplete((data, exception) -> {
				if (ExceptionUtility.sendExceptionally(event, exception)) {
					return;
				}

				if (data == null) {
					event.replyFailure("There are no reaction roles in this server").queue();
					return;
				}

				if (emote != null) {
					List<Document> reactionsData = data.getList("reactions", Document.class, Collections.emptyList());
					Document reaction = reactionsData.stream()
						.filter(ReactionRoleCommand.getReactionFilter(emote))
						.findFirst()
						.orElse(null);

					if (reaction == null) {
						event.replyFailure("You do not have that reaction on that reaction role").queue();
						return;
					}

					List<Document> permissions = reaction.getList("permissions", Document.class, Collections.emptyList());
					if (permissions.stream().anyMatch(d -> d.getLong("id") == holder.getIdLong())) {
						event.replyFailure("That " + (role ? "role" : "user") + " is already whitelisted from that reaction").queue();
						return;
					}
				}

				event.replySuccess((role ? ((Role) holder).getAsMention() : ((Member) holder).getAsMention()) + " is now whitelisted from " + (emote == null ? "all reactions" : "the " + (emote.isEmoji() ? emote.getEmoji() : emote.getEmote().getAsMention()) + " reaction") + " on that reaction role").queue();
			});
		}

		@Command(value="remove", description="Removes a whitelist from a user or role")
		@CommandId(79)
		@Examples({"reaction role whitelist remove 643945552865919002 🐝 @Shea#6653", "reaction role whitelist remove 643945552865919002 :doggo: @Role", "reaction role whitelist remove 643945552865919002 @Role"})
		@AuthorPermissions(permissions={Permission.MANAGE_ROLES})
		public void remove(Sx4CommandEvent event, @Argument(value="message id") MessageArgument messageArgument, @Argument(value="emote", nullDefault=true) ReactionEmote emote, @Argument(value="user | role", endless=true)IPermissionHolder holder) {
			boolean role = holder instanceof Role;

			Bson reactions = Operators.ifNull("$reactions", Collections.EMPTY_LIST);

			List<Bson> update;
			if (emote != null) {
				boolean emoji = emote.isEmoji();

				Bson reaction = Operators.filter(reactions, Operators.eq("$$this.emote." + (emoji ? "name" : "id"), emoji ? emote.getEmoji() : emote.getEmote().getIdLong()));
				Bson permissionsMap = Operators.ifNull(Operators.first(Operators.map(reaction, "$$this.permissions")), Collections.EMPTY_LIST);
				Bson permissionsFilter = Operators.filter(permissionsMap, Operators.ne("$$this.id", holder.getIdLong()));

				Bson result = Operators.concatArrays(List.of(Operators.cond(Operators.isEmpty(permissionsFilter), Operators.removeObject(Operators.first(reaction), "permissions"), Operators.mergeObjects(Operators.first(reaction), new Document("permissions", permissionsFilter)))), Operators.filter(reactions, Operators.ne("$$this.emote." + (emoji ? "name" : "id"), emoji ? emote.getEmoji() : emote.getEmote().getIdLong())));
				update = List.of(Operators.set("reactions", result));
			} else {
				Bson permissionsFilter = Operators.filter("$$this.permissions", Operators.ne("$$this.id", holder.getIdLong()));

				Bson result = Operators.map(reactions, Operators.cond(Operators.isEmpty(permissionsFilter), Operators.removeObject("$$this", "permissions"), Operators.mergeObjects("$$this", new Document("permissions", permissionsFilter))));
				update = List.of(Operators.set("reactions", result));
			}

			FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().projection(Projections.include("reactions")).returnDocument(ReturnDocument.BEFORE);
			event.getDatabase().findAndUpdateReactionRole(Filters.eq("messageId", messageArgument.getMessageId()), update, options).whenComplete((data, exception) -> {
				if (ExceptionUtility.sendExceptionally(event, exception)) {
					return;
				}

				if (data == null) {
					event.replyFailure("There are no reaction roles in this server").queue();
					return;
				}

				if (emote != null) {
					List<Document> reactionsData = data.getList("reactions", Document.class, Collections.emptyList());
					Document reaction = reactionsData.stream()
						.filter(ReactionRoleCommand.getReactionFilter(emote))
						.findFirst()
						.orElse(null);

					if (reaction == null) {
						event.replyFailure("You do not have that reaction on that reaction role").queue();
						return;
					}

					List<Document> permissions = reaction.getList("permissions", Document.class, Collections.emptyList());
					if (permissions.stream().noneMatch(d -> d.getLong("id") == holder.getIdLong())) {
						event.replyFailure("That " + (role ? "role" : "user") + " is not whitelisted from that reaction").queue();
						return;
					}
				}

				event.replySuccess((role ? ((Role) holder).getAsMention() : ((Member) holder).getAsMention()) + " is no longer whitelisted from " + (emote == null ? "any reactions" : "the " + (emote.isEmoji() ? emote.getEmoji() : emote.getEmote().getAsMention()) + " reaction") + " on that reaction role").queue();
			});
		}

		@Command(value="delete", aliases={"remove"}, description="Deletes a whitelist for a reaction or all reactions")
		@CommandId(80)
		@Examples({"reaction role whitelist delete 643945552865919002 :doggo:", "reaction role whitelist delete 643945552865919002"})
		@AuthorPermissions(permissions={Permission.MANAGE_ROLES})
		public void delete(Sx4CommandEvent event, @Argument(value="message id") MessageArgument messageArgument, @Argument(value="emote", nullDefault=true) ReactionEmote emote) {
			Bson reactions = Operators.ifNull("$reactions", Collections.EMPTY_LIST);

			List<Bson> update;
			if (emote != null) {
				boolean emoji = emote.isEmoji();

				Bson reaction = Operators.filter(reactions, Operators.eq("$$this.emote." + (emoji ? "name" : "id"), emoji ? emote.getEmoji() : emote.getEmote().getIdLong()));

				Bson result = Operators.concatArrays(List.of(Operators.mergeObjects(Operators.first(reaction), new Document("permissions", Collections.EMPTY_LIST))), Operators.filter(reactions, Operators.ne("$$this.emote." + (emoji ? "name" : "id"), emoji ? emote.getEmoji() : emote.getEmote().getIdLong())));
				update = List.of(Operators.set("reactions", result));
			} else {
				Bson result = Operators.map(reactions, Operators.mergeObjects("$$this", new Document("permissions", Collections.EMPTY_LIST)));
				update = List.of(Operators.set("reactions", result));
			}

			FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().projection(Projections.include("reactions")).returnDocument(ReturnDocument.BEFORE);
			event.getDatabase().findAndUpdateReactionRole(Filters.eq("messageId", messageArgument.getMessageId()), update, options).whenComplete((data, exception) -> {
				if (ExceptionUtility.sendExceptionally(event, exception)) {
					return;
				}

				if (data == null) {
					event.replyFailure("There are no reaction roles in this server").queue();
					return;
				}

				if (emote != null) {
					List<Document> reactionsData = data.getList("reactions", Document.class, Collections.emptyList());
					Document reaction = reactionsData.stream()
						.filter(ReactionRoleCommand.getReactionFilter(emote))
						.findFirst()
						.orElse(null);

					if (reaction == null) {
						event.replyFailure("You do not have that reaction on that reaction role").queue();
						return;
					}

					List<Document> permissions = reaction.getList("permissions", Document.class, Collections.emptyList());
					if (permissions.isEmpty()) {
						event.replyFailure("That reaction does not have any whitelists").queue();
						return;
					}
				}

				event.replySuccess("There are no longer any whitelists on " + (emote == null ? "any reactions" : "the " + (emote.isEmoji() ? emote.getEmoji() : emote.getEmote().getAsMention()) + " reaction") + " on that reaction role").queue();
			});
		}

	}
	
}
