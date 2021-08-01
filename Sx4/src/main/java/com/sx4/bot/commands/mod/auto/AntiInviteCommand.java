package com.sx4.bot.commands.mod.auto;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.command.Command;
import com.mongodb.client.model.*;
import com.sx4.bot.annotations.argument.Limit;
import com.sx4.bot.annotations.command.AuthorPermissions;
import com.sx4.bot.annotations.command.CommandId;
import com.sx4.bot.annotations.command.Examples;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.database.mongo.MongoDatabase;
import com.sx4.bot.database.mongo.model.Operators;
import com.sx4.bot.entities.argument.TimedArgument;
import com.sx4.bot.entities.management.WhitelistType;
import com.sx4.bot.entities.mod.action.ModAction;
import com.sx4.bot.entities.mod.auto.MatchAction;
import com.sx4.bot.entities.mod.auto.RegexType;
import com.sx4.bot.entities.settings.HolderType;
import com.sx4.bot.handlers.AntiRegexHandler;
import com.sx4.bot.paged.PagedResult;
import com.sx4.bot.utility.ExceptionUtility;
import com.sx4.bot.utility.TimeUtility;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.utils.MarkdownSanitizer;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class AntiInviteCommand extends Sx4Command {

	public static final ObjectId REGEX_ID = new ObjectId("605b5255b656f84eb97fca06");

	public AntiInviteCommand() {
		super("antiinvite", 305);

		super.setDescription("Set up antiinvite to delete any discord invites sent");
		super.setAliases("anti invite", "anti-invite");
		super.setExamples("antiinvite toggle", "antiinvite attempts", "antiinvite mod");
		super.setCategoryAll(ModuleCategory.AUTO_MODERATION);
	}

	public void onCommand(Sx4CommandEvent event) {
		event.replyHelp().queue();
	}

	@Command(value="toggle", description="Toggle the state of anti-invite in the current server")
	@CommandId(306)
	@Examples({"antiinvite toggle"})
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	public void toggle(Sx4CommandEvent event) {
		List<Bson> update = List.of(
			Operators.set("enabled", Operators.cond(Operators.or(Operators.extinct("$type"), Operators.exists("$enabled")), Operators.REMOVE, false)),
			Operators.setOnInsert("pattern", AntiRegexHandler.INVITE_REGEX),
			Operators.setOnInsert("type", RegexType.INVITE.getId())
		);

		Bson filter = Filters.and(Filters.eq("guildId", event.getGuild().getIdLong()), Filters.eq("regexId", AntiInviteCommand.REGEX_ID));
		FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.BEFORE).projection(Projections.include("enabled"));

		event.getMongo().findAndUpdateRegex(filter, update, options).thenCompose(data -> {
			event.replySuccess("Anti-Invite is now " + (data == null || !data.get("enabled", true) ? "enabled" : "disabled")).queue();

			if (data == null) {
				return event.getMongo().updateRegexTemplateById(AntiInviteCommand.REGEX_ID, Updates.inc("uses", 1L));
			} else {
				return CompletableFuture.completedFuture(null);
			}
		}).whenComplete(MongoDatabase.exceptionally(event.getShardManager()));
	}

	/*@Command(value="set", description="Sets the amount of attempts a user has")
	@CommandId(459)
	@Examples({"antiinvite set @Shea#6653 0", "antiinvite set Shea 3", "antiinvite set 402557516728369153 2"})
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	public void set(Sx4CommandEvent event, @Argument(value="user") Member member, @Argument(value="attempts") int attempts) {
		Bson filter = Filters.and(Filters.eq("regexId", AntiInviteCommand.REGEX_ID), Filters.eq("userId", member.getIdLong()), Filters.eq("guildId", event.getGuild().getIdLong()));

		CompletableFuture<Document> future;
		if (attempts == 0) {
			future = event.getMongo().findAndDeleteRegexAttempt(filter);
		} else {
			FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().projection(Projections.include("attempts")).returnDocument(ReturnDocument.BEFORE).upsert(true);
			future = event.getMongo().findAndUpdateRegexAttempt(filter, Updates.set("attempts", attempts), options);
		}

		future.whenComplete((data, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			if (data == null) {
				event.replyFailure("You do not have anti-invite setup").queue();
				return;
			}

			if (data.getInteger("attempts") == attempts) {
				event.replyFailure("That users attempts were already set to that").queue();
				return;
			}

			if (attempts == 0) {
				event.getBot().getAntiRegexManager().clearAttempts(AntiInviteCommand.REGEX_ID, member.getIdLong());
			} else {
				event.getBot().getAntiRegexManager().setAttempts(AntiInviteCommand.REGEX_ID, member.getIdLong(), attempts);
			}

			event.replySuccess("**" + member.getUser().getAsTag() + "** has had their attempts set to **" + attempts + "**").queue();
		});
	}*/

	@Command(value="attempts", description="Sets the amount of attempts needed for the mod action to execute")
	@CommandId(307)
	@Examples({"antiinvite attempts 3", "antiinvite attempts 1"})
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	public void attempts(Sx4CommandEvent event, @Argument(value="attempts") @Limit(min=1) int attempts) {
		Bson filter = Filters.and(Filters.eq("regexId", AntiInviteCommand.REGEX_ID), Filters.eq("guildId", event.getGuild().getIdLong()));
		event.getMongo().updateRegex(filter, Updates.set("attempts.amount", attempts)).whenComplete((result, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			if (result.getMatchedCount() == 0) {
				event.replyFailure("You do not have anti-invite setup").queue();
				return;
			}

			if (result.getModifiedCount() == 0) {
				event.replyFailure("Your attempts where already set to that").queue();
				return;
			}

			event.replySuccess("Attempts to a mod action have been set to **" + attempts + "**").queue();
		});
	}

	@Command(value="reset after", description="The time it should take for attempts to be taken away")
	@CommandId(308)
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	@Examples({"antiinvite reset after 1 1 day", "antiinvite reset after 3 5h 20s", "antiinvite reset after 3 5h 20s"})
	public void resetAfter(Sx4CommandEvent event, @Argument(value="amount") @Limit(min=0) int amount, @Argument(value="time", endless=true, nullDefault=true) Duration time) {
		if (time.toMinutes() < 5) {
			event.replyFailure("The duration has to be 5 minutes or above").queue();
			return;
		}

		Bson update = amount == 0 ? Updates.unset("attempts.reset") : Updates.set("attempts.reset", new Document("amount", amount).append("after", time.toSeconds()));
		Bson filter = Filters.and(Filters.eq("regexId", AntiInviteCommand.REGEX_ID), Filters.eq("guildId", event.getGuild().getIdLong()));

		event.getMongo().updateRegex(filter, update).whenComplete((result, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			if (result.getMatchedCount() == 0) {
				event.replyFailure("You do not have anti-invite setup").queue();
				return;
			}

			if (result.getModifiedCount() == 0) {
				event.replyFailure("Your reset attempts configuration was already set to that").queue();
				return;
			}

			event.reply(amount == 0 ? "Users attempts will no longer reset" + event.getConfig().getSuccessEmote() : String.format("Users attempts will now reset **%d** time%s after `%s` %s", amount, amount == 1 ? "" : "s", TimeUtility.getTimeString(time.toSeconds()), event.getConfig().getSuccessEmote())).queue();
		});
	}

	public static class ModCommand extends Sx4Command {

		public ModCommand() {
			super("mod", 309);

			super.setDescription("Set specific things to happen when someone reaches a certain amount of attempts");
			super.setExamples("anti regex mod message", "anti regex mod action");
			super.setCategoryAll(ModuleCategory.AUTO_MODERATION);
		}

		public void onCommand(Sx4CommandEvent event) {
			event.replyHelp().queue();
		}

		@Command(value="message", description="Changes the message which is sent when someone hits the max attempts")
		@CommandId(310)
		@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
		@Examples({"anti regex mod message A user has been banned for sending links", "anti regex match message {user.name} has received a {regex.action}"})
		public void message(Sx4CommandEvent event, @Argument(value="message", endless=true) @Limit(max=1500) String message) {
			Bson filter = Filters.and(Filters.eq("regexId", AntiInviteCommand.REGEX_ID), Filters.eq("guildId", event.getGuild().getIdLong()));
			event.getMongo().updateRegex(filter, Updates.set("mod.message", message)).whenComplete((result, exception) -> {
				if (ExceptionUtility.sendExceptionally(event, exception)) {
					return;
				}

				if (result.getMatchedCount() == 0) {
					event.replyFailure("You do not have anti-invite setup").queue();
					return;
				}

				if (result.getModifiedCount() == 0) {
					event.replyFailure("Your mod message for that regex was already set to that").queue();
					return;
				}

				event.replySuccess("Your mod message for that regex has been updated").queue();
			});
		}

		@Command(value="action", description="Sets the action to be taken when a user hits the max attempts")
		@CommandId(311)
		@Examples({"anti regex mod action WARN", "anti regex mod action MUTE 60m"})
		@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
		public void action(Sx4CommandEvent event, @Argument(value="action", endless=true) TimedArgument<ModAction> timedAction) {
			ModAction action = timedAction.getArgument();
			if (!action.isOffence()) {
				event.replyFailure("The action has to be an offence").queue();
				return;
			}

			Document modAction = new Document("type", action.getType());

			if (action.isTimed()) {
				Duration duration = timedAction.getDuration();
				if (duration == null) {
					event.replyFailure("You need to provide a duration for this mod action").queue();
					return;
				}

				modAction.append("duration", duration.toSeconds());
			}

			Bson filter = Filters.and(Filters.eq("regexId", AntiInviteCommand.REGEX_ID), Filters.eq("guildId", event.getGuild().getIdLong()));
			event.getMongo().updateRegex(filter, Updates.set("mod.action", modAction)).whenComplete((result, exception) -> {
				if (ExceptionUtility.sendExceptionally(event, exception)) {
					return;
				}

				if (result.getMatchedCount() == 0) {
					event.replyFailure("You do not have anti-invite setup").queue();
					return;
				}

				if (result.getModifiedCount() == 0) {
					event.replyFailure("Your mod action for this regex is already set to that").queue();
					return;
				}

				event.replySuccess("Your mod action for that regex has been updated").queue();
			});
		}

	}

	public static class MatchCommand extends Sx4Command {

		public MatchCommand() {
			super("match", 312);

			super.setDescription("Set specific things to happen when a message is matched with a specific regex");
			super.setExamples("anti regex match action", "anti regex match message");
			super.setCategoryAll(ModuleCategory.AUTO_MODERATION);
		}

		public void onCommand(Sx4CommandEvent event) {
			event.replyHelp().queue();
		}

		@Command(value="message", description="Changes the message which is sent when someone sends an invite")
		@CommandId(313)
		@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
		@Examples({"anti regex match message You cannot have a url in your message :no_entry:", "anti regex match message {user.mention}, don't send that here or else you'll get a {regex.action} :no_entry:"})
		public void message(Sx4CommandEvent event, @Argument(value="message", endless=true) @Limit(max=1500) String message) {
			Bson filter = Filters.and(Filters.eq("regexId", AntiInviteCommand.REGEX_ID), Filters.eq("guildId", event.getGuild().getIdLong()));
			event.getMongo().updateRegex(filter, Updates.set("match.message", message)).whenComplete((result, exception) -> {
				if (ExceptionUtility.sendExceptionally(event, exception)) {
					return;
				}

				if (result.getMatchedCount() == 0) {
					event.replyFailure("You do not have anti-invite setup").queue();
					return;
				}

				if (result.getModifiedCount() == 0) {
					event.replyFailure("Your match message for that regex was already set to that").queue();
					return;
				}

				event.replySuccess("Your match message for that regex has been updated").queue();
			});
		}

		@Command(value="action", description="Set what the bot should do when someone sends an invite")
		@CommandId(314)
		@Examples({"anti regex match action SEND_MESSAGE", "anti regex match action SEND_MESSAGE DELETE_MESSAGE"})
		@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
		public void action(Sx4CommandEvent event, @Argument(value="actions") MatchAction... actions) {
			Bson filter = Filters.and(Filters.eq("regexId", AntiInviteCommand.REGEX_ID), Filters.eq("guildId", event.getGuild().getIdLong()));
			event.getMongo().updateRegex(filter, Updates.set("match.action", MatchAction.getRaw(actions))).whenComplete((result, exception) -> {
				if (ExceptionUtility.sendExceptionally(event, exception)) {
					return;
				}

				if (result.getMatchedCount() == 0) {
					event.replyFailure("You do not have anti-invite setup").queue();
					return;
				}

				if (result.getModifiedCount() == 0) {
					event.replyFailure("Your match action for this regex is already set to that").queue();
					return;
				}

				event.replySuccess("Your match action for that regex has been updated").queue();
			});
		}

	}

	public static class WhitelistCommand extends Sx4Command {

		public WhitelistCommand() {
			super("whitelist", 315);

			super.setDescription("Whitelist roles and users from certain channels so they can ignore the anti regex");
			super.setExamples("anti regex whitelist add", "anti regex whitelist remove");
			super.setCategoryAll(ModuleCategory.AUTO_MODERATION);
		}

		public void onCommand(Sx4CommandEvent event) {
			event.replyHelp().queue();
		}

		@Command(value="add", description="Adds a whitelist for a role or user")
		@CommandId(316)
		@Examples({"anti regex whitelist add #channel @everyone", "anti regex whitelist add @Shea#6653"})
		@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
		public void add(Sx4CommandEvent event, @Argument(value="channel", nullDefault=true) TextChannel channelArgument, @Argument(value="user | role", endless=true) IPermissionHolder holder) {
			List<TextChannel> channels = channelArgument == null ? event.getGuild().getTextChannels() : List.of(channelArgument);

			Bson channelMap = Operators.ifNull("$whitelist", Collections.EMPTY_LIST);

			boolean role = holder instanceof Role;
			long holderId = holder.getIdLong();

			Document holderData = new Document("id", holderId).append("type", role ? HolderType.ROLE.getType() : HolderType.USER.getType());

			List<Bson> concat = new ArrayList<>();
			List<Long> channelIds = new ArrayList<>();
			for (TextChannel channel : channels) {
				long channelId = channel.getIdLong();
				channelIds.add(channelId);

				Document channelData = new Document("id", channelId).append("type", WhitelistType.CHANNEL.getId()).append("holders", List.of(holderData));

				Bson channelFilter = Operators.filter(channelMap, Operators.eq("$$this.id", channelId));
				concat.add(Operators.cond(Operators.isEmpty(channelFilter), List.of(channelData), List.of(Operators.mergeObjects(Operators.first(channelFilter), new Document("holders", Operators.concatArrays(List.of(holderData), Operators.filter(Operators.ifNull(Operators.first(Operators.map(channelFilter, "$$this.holders")), Collections.EMPTY_LIST), Operators.ne("$$this.id", holderId))))))));
			}

			concat.add(Operators.filter(channelMap, Operators.not(Operators.in("$$this.id", channelIds))));
			List<Bson> update = List.of(Operators.set("whitelist", Operators.concatArrays(concat)));
			Bson filter = Filters.and(Filters.eq("regexId", AntiInviteCommand.REGEX_ID), Filters.eq("guildId", event.getGuild().getIdLong()));

			event.getMongo().updateRegex(filter, update).whenComplete((result, exception) -> {
				if (ExceptionUtility.sendExceptionally(event, exception)) {
					return;
				}

				if (result.getMatchedCount() == 0) {
					event.replyFailure("You do not have anti-invite setup").queue();
					return;
				}

				if (result.getModifiedCount() == 0) {
					event.replyFailure((role ? ((Role) holder).getAsMention() : ((Member) holder).getUser().getAsMention()) + " is already whitelisted in all of the provided channels").queue();
					return;
				}

				event.replySuccess((role ? ((Role) holder).getAsMention() : ((Member) holder).getUser().getAsMention()) + " is now whitelisted in the provided channels").queue();
			});
		}


		@Command(value="remove", description="Removes a role or user whitelist from channels")
		@CommandId(317)
		@Examples({"antiinvite whitelist remove #channel @everyone", "antiinvite whitelist remove @Shea#6653"})
		@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
		public void remove(Sx4CommandEvent event, @Argument(value="channel", nullDefault=true) TextChannel channelArgument, @Argument(value="user | role") IPermissionHolder holder) {
			List<TextChannel> channels = channelArgument == null ? event.getGuild().getTextChannels() : List.of(channelArgument);

			Bson channelMap = Operators.ifNull("$whitelist", Collections.EMPTY_LIST);

			boolean role = holder instanceof Role;
			long holderId = holder.getIdLong();

			Document holderData = new Document("id", holderId).append("type", role ? HolderType.ROLE.getType() : HolderType.USER.getType());

			List<Bson> concat = new ArrayList<>();
			List<Long> channelIds = new ArrayList<>();
			for (TextChannel channel : channels) {
				long channelId = channel.getIdLong();
				channelIds.add(channelId);

				Document channelData = new Document("id", channelId).append("type", WhitelistType.CHANNEL.getId()).append("holders", List.of(holderData));

				Bson channelFilter = Operators.filter(channelMap, Operators.eq("$$this.id", channelId));
				concat.add(Operators.cond(Operators.isEmpty(channelFilter), List.of(channelData), List.of(Operators.mergeObjects(Operators.first(channelFilter), new Document("holders", Operators.filter(Operators.ifNull(Operators.first(Operators.map(channelFilter, "$$this.holders")), Collections.EMPTY_LIST), Operators.ne("$$this.id", holderId)))))));
			}

			concat.add(Operators.filter(channelMap, Operators.not(Operators.in("$$this.id", channelIds))));
			List<Bson> update = List.of(Operators.set("whitelist", Operators.concatArrays(concat)));
			Bson filter = Filters.and(Filters.eq("regexId", AntiInviteCommand.REGEX_ID), Filters.eq("guildId", event.getGuild().getIdLong()));

			event.getMongo().updateRegex(filter, update).whenComplete((result, exception) -> {
				if (ExceptionUtility.sendExceptionally(event, exception)) {
					return;
				}

				if (result.getMatchedCount() == 0) {
					event.replyFailure("You do not have anti-invite setup").queue();
					return;
				}

				if (result.getModifiedCount() == 0) {
					event.replyFailure((role ? ((Role) holder).getAsMention() : ((Member) holder).getUser().getAsMention()) + " is not whitelisted in any of the provided channels").queue();
					return;
				}

				event.replySuccess((role ? ((Role) holder).getAsMention() : ((Member) holder).getUser().getAsMention()) + " is no longer whitelisted in the provided channels").queue();
			});
		}

		@Command(value="list", description="Lists roles and users that are whitelisted from specific channels for anti-invite")
		@CommandId(318)
		@Examples({"antiinvite whitelist list 5f023782ef9eba03390a740c"})
		public void list(Sx4CommandEvent event, @Argument(value="id") ObjectId id, @Argument(value="channels", nullDefault=true) TextChannel channel) {
			List<TextChannel> channels = channel == null ? event.getGuild().getTextChannels() : List.of(channel);

			Document regex = event.getMongo().getRegex(Filters.and(Filters.eq("type", RegexType.INVITE), Filters.eq("guildId", event.getGuild().getIdLong())), Projections.include("whitelist"));
			if (regex == null) {
				event.replyFailure("You do not have anti-invite setup").queue();
				return;
			}

			PagedResult<TextChannel> channelPaged = new PagedResult<>(event.getBot(), channels)
				.setAutoSelect(true)
				.setAuthor("Channels", null, event.getGuild().getIconUrl())
				.setDisplayFunction(TextChannel::getAsMention);

			channelPaged.onSelect(channelSelect -> {
				TextChannel selectedChannel = channelSelect.getSelected();

				Document whitelist = regex.getList("whitelist", Document.class).stream()
					.filter(w -> w.getLong("id") == selectedChannel.getIdLong())
					.findFirst()
					.orElse(null);

				if (whitelist == null) {
					event.replyFailure("Nothing is whitelisted for anti-invite in " + selectedChannel.getAsMention()).queue();
					return;
				}

				PagedResult<String> typePaged = new PagedResult<>(event.getBot(), List.of("Groups", "Users/Roles"))
					.setAuthor("Type", null, event.getGuild().getIconUrl())
					.setDisplayFunction(String::toString);

				typePaged.onSelect(typeSelect -> {
					String typeSelected = typeSelect.getSelected();

					boolean groups = typeSelected.equals("Groups");

					List<Document> whitelists = whitelist.getList(groups ? "groups" : "holders", Document.class, Collections.emptyList());
					if (whitelists.isEmpty()) {
						event.replyFailure("Nothing is whitelisted in " + typeSelected.toLowerCase() + " for anti-invite in " + selectedChannel.getAsMention()).queue();
						return;
					}

					PagedResult<Document> whitelistPaged = new PagedResult<>(event.getBot(), whitelists)
						.setAuthor(typeSelected, null, event.getGuild().getIconUrl())
						.setDisplayFunction(data -> {
							if (groups) {
								return "Group " + data.getInteger("group");
							} else {
								long holderId = data.getLong("id");
								int type = data.getInteger("type");
								if (type == HolderType.ROLE.getType()) {
									Role role = event.getGuild().getRoleById(holderId);
									return role == null ? "Deleted Role (" + holderId + ")" : role.getAsMention();
								} else {
									User user = event.getShardManager().getUserById(holderId);
									return user == null ? "Unknown User (" + holderId + ")" : user.getAsTag();
								}
							}
						});

					if (!groups) {
						whitelistPaged.setSelect().setIndexed(false);
					}

					whitelistPaged.onSelect(whitelistSelect -> {
						List<String> strings = whitelistSelect.getSelected().getList("strings", String.class, Collections.emptyList());
						if (strings.isEmpty()) {
							event.replyFailure("No strings are whitelisted in this group").queue();
							return;
						}

						PagedResult<String> stringPaged = new PagedResult<>(event.getBot(), strings)
							.setAuthor("Strings", null, event.getGuild().getIconUrl())
							.setDisplayFunction(MarkdownSanitizer::sanitize)
							.setSelect()
							.setIndexed(false);

						stringPaged.execute(event);
					});

					whitelistPaged.execute(event);
				});

				typePaged.execute(event);
			});

			channelPaged.execute(event);
		}
	}

}
