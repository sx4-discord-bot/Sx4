package com.sx4.bot.commands.management;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.command.Command;
import com.jockie.bot.core.command.Command.Cooldown;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.sx4.bot.annotations.argument.Limit;
import com.sx4.bot.annotations.argument.Options;
import com.sx4.bot.annotations.command.AuthorPermissions;
import com.sx4.bot.annotations.command.CommandId;
import com.sx4.bot.annotations.command.Examples;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.database.mongo.model.Operators;
import com.sx4.bot.entities.argument.Alternative;
import com.sx4.bot.entities.argument.MessageArgument;
import com.sx4.bot.entities.settings.HolderType;
import com.sx4.bot.utility.ExceptionUtility;
import com.sx4.bot.utility.PermissionUtility;
import com.sx4.bot.waiter.Waiter;
import com.sx4.bot.waiter.exception.CancelException;
import com.sx4.bot.waiter.exception.TimeoutException;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.IPermissionHolder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageReaction.ReactionEmote;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.interactions.components.Button;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.internal.requests.CompletedRestAction;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CompletionException;

public class ReactionRoleCommand extends Sx4Command {
	
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

		if (!event.getSelfMember().hasPermission(messageArgument.getChannel(), Permission.MESSAGE_HISTORY)) {
			event.replyFailure(PermissionUtility.formatMissingPermissions(EnumSet.of(Permission.MESSAGE_HISTORY), "I am")).queue();
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

			if (!event.getSelfMember().hasPermission(message.getTextChannel(), Permission.MESSAGE_ADD_REACTION)) {
				event.replyFailure(PermissionUtility.formatMissingPermissions(EnumSet.of(Permission.MESSAGE_ADD_REACTION), "I am")).queue();
				return;
			}

			Bson filter = Filters.and(
				Filters.eq("messageId", message.getIdLong()),
				Filters.eq("emote", new Document(identifier, unicode ? emote.getEmoji() : emote.getEmote().getIdLong()))
			);

			Bson update = Updates.combine(
				Updates.addToSet("roles", role.getIdLong()),
				Updates.setOnInsert("guildId", event.getGuild().getIdLong()),
				Updates.setOnInsert("channelId", message.getChannel().getIdLong())
			);

			RestAction<Void> action;
			if (unicode && message.getReactionByUnicode(emote.getEmoji()) == null) {
				action = message.addReaction(emote.getEmoji());
			} else {
				if (!unicode && message.getReactionById(emote.getEmote().getIdLong()) == null) {
					message.addReaction(emote.getEmote()).queue();
				}

				action = new CompletedRestAction<>(event.getJDA(), null);
			}

			action.submit()
				.thenCompose($ -> event.getMongo().updateReactionRole(filter, update))
				.whenComplete((result, exception) -> {
					if (exception instanceof ErrorResponseException) {
						ErrorResponseException errorResponse = ((ErrorResponseException) exception);
						if (errorResponse.getErrorCode() == 400 || errorResponse.getErrorResponse() == ErrorResponse.UNKNOWN_EMOJI) {
							event.replyFailure("I could not find that emote").queue();
							return;
						}
					}

					if (ExceptionUtility.sendExceptionally(event, exception)) {
						return;
					}

					if (result.getModifiedCount() == 0 && result.getUpsertedId() == null) {
						event.replyFailure("That role is already given when reacting to this reaction").queue();
						return;
					}

					event.replySuccess("The role " + role.getAsMention() + " will now be given when reacting to " + (unicode ? emote.getEmoji() : emote.getEmote().getAsMention())).queue();
				});
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

		Bson filter = Filters.and(
			Filters.eq("messageId", messageArgument.getMessageId()),
			Filters.eq("emote", new Document(identifier, unicode ? emote.getEmoji() : emote.getEmote().getIdLong()))
		);

		Bson update = Updates.pull("roles", role.getIdLong());

		event.getMongo().updateReactionRole(filter, update).whenComplete((result, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			if (result.getMatchedCount() == 0) {
				event.replyFailure("You do not have that reaction on that reaction role").queue();
				return;
			}

			if (result.getModifiedCount() == 0 && result.getUpsertedId() == null) {
				event.replyFailure("That role is not given when reacting to that emote").queue();
				return;
			}

			event.replySuccess("The role " + role.getAsMention() + " has been removed from that reaction").queue();
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

		event.getMongo().updateManyReactionRoles(filter, update, new UpdateOptions()).whenComplete((result, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}
			
			if (result.getModifiedCount() == 0 && result.getMatchedCount() != 0) {
				event.replyFailure((alternative ? "All your reaction roles" : "That reaction role") + " already " + (alternative ? "have" : "has") + " it set to " + (enable ? "" : "not ") + "dm users").queue();
				return;
			}
			
			if (result.getModifiedCount() == 0) {
				event.replyFailure(alternative ? "You do not have any reaction roles setup " + event.getConfig().getFailureEmote() : "You do not have that reaction on that reaction role").queue();
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

		event.getMongo().updateManyReactionRoles(filter, update, new UpdateOptions()).whenComplete((result, exception) -> {
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
			List<Button> buttons = List.of(Button.success("yes", "Yes"), Button.danger("no", "No"));

			event.reply(event.getAuthor().getName() + ", are you sure you want to delete **all** the reaction roles in this server?").setActionRow(buttons).submit()
				.thenCompose(message -> {
					return new Waiter<>(event.getBot(), ButtonClickEvent.class)
						.setPredicate(e -> {
							Button button = e.getButton();
							return button != null && button.getId().equals("yes") && e.getMessageIdLong() == message.getIdLong() && e.getUser().getIdLong() == event.getAuthor().getIdLong();
						})
						.setCancelPredicate(e -> {
							Button button = e.getButton();
							return button != null && button.getId().equals("no") && e.getMessageIdLong() == message.getIdLong() && e.getUser().getIdLong() == event.getAuthor().getIdLong();
						})
						.setTimeout(60)
						.start();
				})
				.thenCompose(messageEvent -> event.getMongo().deleteManyReactionRoles(Filters.eq("guildId", event.getGuild().getIdLong())))
				.whenComplete((result, exception) -> {
					Throwable cause = exception instanceof CompletionException ? exception.getCause() : exception;
					if (cause instanceof CancelException) {
						event.replySuccess("Cancelled").queue();
						return;
					} else if (cause instanceof TimeoutException) {
						event.reply("Timed out :stopwatch:").queue();
						return;
					} else if (ExceptionUtility.sendExceptionally(event, cause)) {
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
			event.getMongo().deleteManyReactionRoles(Filters.eq("messageId", messageId)).whenComplete((result, exception) -> {
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
			super.setCategoryAll(ModuleCategory.MANAGEMENT);
		}

		public void onCommand(Sx4CommandEvent event) {
			event.replyHelp().queue();
		}

		@Command(value="add", description="Adds a whitelist for a user or role to be able to use a reaction on a reaction role")
		@CommandId(78)
		@Examples({"reaction role whitelist add 643945552865919002 🐝 @Shea#6653", "reaction role whitelist add 643945552865919002 :doggo: @Role", "reaction role whitelist add 643945552865919002 @Role"})
		@AuthorPermissions(permissions={Permission.MANAGE_ROLES})
		public void add(Sx4CommandEvent event, @Argument(value="message id") MessageArgument messageArgument, @Argument(value="emote", nullDefault=true) ReactionEmote emote, @Argument(value="user | role", endless=true) IPermissionHolder holder) {
			boolean role = holder instanceof Role;

			Document holderData = new Document("id", holder.getIdLong())
				.append("type", role ? HolderType.ROLE.getType() : HolderType.USER.getType());

			Bson filter = Filters.eq("messageId", messageArgument.getMessageId());

			List<Bson> update;
			if (emote != null) {
				boolean unicode = emote.isEmoji();

				filter = Filters.and(filter, Filters.eq("emote", new Document(unicode ? "name" : "id", unicode ? emote.getEmoji() : emote.getEmote().getIdLong())));

				Bson permissionsMap = Operators.ifNull("$permissions", Collections.EMPTY_LIST);
				Bson holderFilter = Operators.filter(permissionsMap, Operators.eq("$$this.id", holder.getIdLong()));
				Bson holderMap = Operators.ifNull(Operators.first(holderFilter), holderData);

				Bson result = Operators.concatArrays(Operators.filter(permissionsMap, Operators.ne("$$this.id", holder.getIdLong())), List.of(Operators.mergeObjects(holderMap, new Document("granted", true))));
				update = List.of(Operators.set("permissions", result));
			} else {
				Bson result = Operators.concatArrays(Operators.filter(Operators.ifNull("$permissions", Collections.EMPTY_LIST), Operators.ne("$$this.id", holder.getIdLong())), List.of(Operators.mergeObjects(Operators.ifNull(Operators.first(Operators.filter(Operators.ifNull("$permissions", Collections.EMPTY_LIST), Operators.eq("$$this.id", holder.getIdLong()))), holderData), new Document("granted", true))));
				update = List.of(Operators.set("permissions", result));
			}

			event.getMongo().updateManyReactionRoles(filter, update, new UpdateOptions()).whenComplete((result, exception) -> {
				if (ExceptionUtility.sendExceptionally(event, exception)) {
					return;
				}

				if (emote != null) {
					if (result.getMatchedCount() == 0) {
						event.replyFailure("You do not have that reaction on that reaction role").queue();
						return;
					}

					if (result.getModifiedCount() == 0) {
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
		public void remove(Sx4CommandEvent event, @Argument(value="message id") MessageArgument messageArgument, @Argument(value="emote", nullDefault=true) ReactionEmote emote, @Argument(value="user | role", endless=true) IPermissionHolder holder) {
			boolean role = holder instanceof Role;

			Bson filter = Filters.eq("messageId", messageArgument.getMessageId());
			Bson update = Updates.pull("permissions", Filters.eq("id", holder.getIdLong()));

			if (emote != null) {
				boolean unicode = emote.isEmoji();

				filter = Filters.and(filter, Filters.eq("emote", new Document(unicode ? "name" : "id", unicode ? emote.getEmoji() : emote.getEmote().getIdLong())));
			}

			event.getMongo().updateManyReactionRoles(filter, update, new UpdateOptions()).whenComplete((result, exception) -> {
				if (ExceptionUtility.sendExceptionally(event, exception)) {
					return;
				}

				if (emote != null) {
					if (result.getMatchedCount() == 0) {
						event.replyFailure("You do not have that reaction on that reaction role").queue();
						return;
					}

					if (result.getModifiedCount() == 0) {
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
			Bson filter = Filters.eq("messageId", messageArgument.getMessageId());
			Bson update = Updates.unset("permissions");

			if (emote != null) {
				boolean unicode = emote.isEmoji();

				filter = Filters.and(filter, Filters.eq("emote", new Document(unicode ? "name" : "id", unicode ? emote.getEmoji() : emote.getEmote().getIdLong())));
			}

			event.getMongo().updateManyReactionRoles(filter, update, new UpdateOptions()).whenComplete((result, exception) -> {
				if (ExceptionUtility.sendExceptionally(event, exception)) {
					return;
				}

				if (emote != null) {
					if (result.getMatchedCount() == 0) {
						event.replyFailure("You do not have that reaction on that reaction role").queue();
						return;
					}

					if (result.getModifiedCount() == 0) {
						event.replyFailure("That reaction does not have any whitelists").queue();
						return;
					}
				}

				event.replySuccess("There are no longer any whitelists on " + (emote == null ? "any reactions" : "the " + (emote.isEmoji() ? emote.getEmoji() : emote.getEmote().getAsMention()) + " reaction") + " on that reaction role").queue();
			});
		}

	}
	
}
