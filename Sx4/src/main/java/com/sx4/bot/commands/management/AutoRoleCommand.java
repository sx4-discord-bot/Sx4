package com.sx4.bot.commands.management;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.command.Command;
import com.mongodb.ErrorCategory;
import com.mongodb.MongoWriteException;
import com.mongodb.client.model.*;
import com.sx4.bot.annotations.argument.AlternativeOptions;
import com.sx4.bot.annotations.command.AuthorPermissions;
import com.sx4.bot.annotations.command.BotPermissions;
import com.sx4.bot.annotations.command.CommandId;
import com.sx4.bot.annotations.command.Examples;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.database.mongo.model.Operators;
import com.sx4.bot.entities.argument.Alternative;
import com.sx4.bot.entities.argument.TimedArgument;
import com.sx4.bot.entities.management.AutoRoleFilter;
import com.sx4.bot.paged.PagedResult;
import com.sx4.bot.utility.AutoRoleUtility;
import com.sx4.bot.utility.ExceptionUtility;
import com.sx4.bot.utility.FutureUtility;
import com.sx4.bot.utility.TimeUtility;
import com.sx4.bot.waiter.Waiter;
import com.sx4.bot.waiter.exception.CancelException;
import com.sx4.bot.waiter.exception.TimeoutException;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.interactions.components.Button;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;

public class AutoRoleCommand extends Sx4Command {

	public AutoRoleCommand() {
		super("auto role", 37);
		
		super.setDescription("Sets roles to be given when a user joins the server");
		super.setAliases("autorole");
		super.setExamples("auto role toggle", "auto role add", "auto role remove");
		super.setCategoryAll(ModuleCategory.MANAGEMENT);
	}
	
	public void onCommand(Sx4CommandEvent event) {
		event.replyHelp().queue();
	}
	
	@Command(value="toggle", description="Toggles the state of an auto role")
	@CommandId(38)
	@Examples({"auto role toggle @Role", "auto role toggle Role", "auto role toggle 406240455622262784"})
	@AuthorPermissions(permissions={Permission.MANAGE_ROLES})
	public void toggle(Sx4CommandEvent event, @Argument(value="role", endless=true) Role role) {
		List<Bson> update = List.of(Operators.set("enabled", Operators.cond(Operators.exists("$enabled"), Operators.REMOVE, false)));
		FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().projection(Projections.include("enabled")).returnDocument(ReturnDocument.AFTER);

		event.getMongo().findAndUpdateAutoRole(Filters.eq("roleId", role.getIdLong()), update, options).whenComplete((data, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}
			
			event.replySuccess("That auto role is now **" + (data.get("enabled", true) ? "enabled" : "disabled") + "**").queue();
		});
	}
	
	@Command(value="add", description="Add a role to be given when a user joins")
	@CommandId(39)
	@Examples({"auto role add @Role", "auto role add Role", "auto role add 406240455622262784"})
	@AuthorPermissions(permissions={Permission.MANAGE_ROLES})
	public void add(Sx4CommandEvent event, @Argument(value="role", endless=true) Role role) {
		if (role.isManaged()) {
			event.replyFailure("You cannot add a managed role as an auto role").queue();
			return;
		}
		
		if (role.isPublicRole()) {
			event.replyFailure("You cannot add the @everyone role as an auto role").queue();
			return;
		}
		
		if (!event.getSelfMember().canInteract(role)) {
			event.replyFailure("You cannot add an auto role higher or equal than my top role").queue();
			return;
		}
		
		if (!event.getMember().canInteract(role)) {
			event.replyFailure("You cannot give n auto role higher or equal than your top role").queue();
			return;
		}
		
		Document data = new Document("roleId", role.getIdLong())
			.append("guildId", event.getGuild().getIdLong());

		event.getMongo().insertAutoRole(data).whenComplete((result, exception) -> {
			Throwable cause = exception instanceof CompletionException ? exception.getCause() : exception;
			if (cause instanceof MongoWriteException && ((MongoWriteException) cause).getError().getCategory() == ErrorCategory.DUPLICATE_KEY) {
				event.replyFailure("That role is already an auto role").queue();
				return;
			}

			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}
			
			event.replySuccess("The role " + role.getAsMention() + " has been added as an auto role").queue();
		});
	}
	
	@Command(value="remove", aliases={"delete"}, description="Remove a role from being given when a user joins")
	@CommandId(40)
	@Examples({"auto role remove @Role", "auto role remove Role", "auto role remove all"})
	@AuthorPermissions(permissions={Permission.MANAGE_ROLES})
	public void remove(Sx4CommandEvent event, @Argument(value="role | all", endless=true) @AlternativeOptions("all") Alternative<Role> option) {
		if (option.isAlternative()) {
			List<Button> buttons = List.of(Button.success("yes", "Yes"), Button.danger("no", "No"));

			event.reply(event.getAuthor().getName() + ", are you sure you want to remove every auto role in the server?").setActionRow(buttons).submit()
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
						.onFailure(e -> e.reply("This is not your button to click " + event.getConfig().getFailureEmote()).setEphemeral(true).queue())
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

					event.getMongo().deleteManyAutoRoles(Filters.eq("guildId", event.getGuild().getIdLong())).whenComplete((result, databaseException) -> {
						if (ExceptionUtility.sendExceptionally(event, databaseException)) {
							return;
						}

						if (result.getDeletedCount() == 0) {
							e.reply("There are no auto roles in this server " + event.getConfig().getFailureEmote()).queue();
							return;
						}

						e.reply("All auto roles have been removed " + event.getConfig().getSuccessEmote()).queue();
					});
				});
		} else {
			Role role = option.getValue();
			event.getMongo().deleteAutoRole(Filters.eq("roleId", role.getIdLong())).whenComplete((result, exception) -> {
				if (ExceptionUtility.sendExceptionally(event, exception)) {
					return;
				}
				
				if (result.getDeletedCount() == 0) {
					event.replyFailure("That role is not an auto role").queue();
					return;
				}
				
				event.replySuccess(role.getAsMention() + " is no longer an auto role").queue();
			});
		}
	}

	private final Set<Long> pending = new HashSet<>();

	@Command(value="fix", description="Will give missing members the auto role if needed")
	@CommandId(458)
	@Examples({"auto role fix @Role", "auto role fix Role", "auto role fix 406240455622262784"})
	@AuthorPermissions(permissions={Permission.MANAGE_SERVER})
	@BotPermissions(permissions={Permission.MANAGE_ROLES})
	public void fix(Sx4CommandEvent event, @Argument(value="role", endless=true) Role role) {
		Document data = event.getMongo().getAutoRole(Filters.eq("roleId", role.getIdLong()), Projections.include("filters"));
		if (data == null) {
			event.replyFailure("That role is not an auto role").queue();
			return;
		}

		if (!event.getSelfMember().canInteract(role)) {
			event.replyFailure("That auto role is above my top role").queue();
			return;
		}

		List<Document> filters = data.getList("filters", Document.class, Collections.emptyList());

		List<Member> members = event.getGuild().getMemberCache().applyStream(stream -> stream.filter(member -> AutoRoleUtility.filtersMatch(member, filters) && !member.getRoles().contains(role) && !member.isPending()).collect(Collectors.toList()));
		if (members.size() == 0) {
			event.replyFailure("No users currently need that auto role").queue();
			return;
		}

		if (!this.pending.add(event.getGuild().getIdLong())) {
			event.replyFailure("You already have an auto role fix in progress").queue();
			return;
		}

		event.replyFormat("Adding %s to **%,d** user%s, another message will be sent once this is done %s", role.getAsMention(), members.size(), members.size() == 1 ? "" : "s", event.getConfig().getSuccessEmote()).queue();

		List<CompletableFuture<Integer>> futures = new ArrayList<>();
		for (Member member : members) {
			futures.add(event.getGuild().addRoleToMember(member, role).submit().handle((result, exception) -> exception == null ? 1 : 0));
		}

		FutureUtility.allOf(futures).whenComplete((completed, exception) -> {
			this.pending.remove(event.getGuild().getIdLong());

			int count = completed.stream().reduce(0, Integer::sum);
			event.replyFormat("Successfully added the role %s to **%,d/%,d** user%s %s", role.getAsMention(), count, count == 1 ? "" : "s", members.size(), event.getConfig().getSuccessEmote()).queue();
		});
	}
	
	@Command(value="list", description="Lists all the auto roles setup")
	@CommandId(41)
	@Examples({"auto role list"})
	public void list(Sx4CommandEvent event) {
		List<Document> data = event.getMongo().getAutoRoles(Filters.eq("guildId", event.getGuild().getIdLong()), Projections.include("roleId")).into(new ArrayList<>());
		if (data.isEmpty()) {
			event.replyFailure("You have no auto roles setup").queue();
			return;
		}
		
		List<Role> roles = data.stream()
			.map(d -> d.getLong("roleId"))
			.map(event.getGuild()::getRoleById)
			.filter(Objects::nonNull)
			.collect(Collectors.toList());
		
		PagedResult<Role> paged = new PagedResult<>(event.getBot(), roles)
			.setAuthor("Auto Roles", null, event.getGuild().getIconUrl())
			.setIndexed(false)
			.setSelect()
			.setDisplayFunction(Role::getAsMention);
		
		paged.execute(event);
	}
	
	public static class FilterCommand extends Sx4Command {
		
		public FilterCommand() {
			super("filter", 42);
			
			super.setDescription("Add or remove filters from auto roles");
			super.setExamples("auto role filter add", "auto role filter remove", "auto role filter list");
			super.setCategoryAll(ModuleCategory.MANAGEMENT);
		}
		
		public void onCommand(Sx4CommandEvent event) {
			event.replyHelp().queue();
		}
		
		@Command(value="add", description="Adds a filter to an auto role")
		@CommandId(43)
		@Examples({"auto role filter add @Role BOT", "auto role filter add Role NOT_BOT"})
		@AuthorPermissions(permissions={Permission.MANAGE_ROLES})
		public void add(Sx4CommandEvent event, @Argument(value="role") Role role, @Argument(value="filter", endless=true) TimedArgument<AutoRoleFilter> timedArgument) {
			AutoRoleFilter filter = timedArgument.getArgument();
			if (filter.hasDuration() && !timedArgument.hasDuration()) {
				event.replyFailure("That filter requires a time interval to be given with it").queue();
				return;
			}

			Document filterData;
			if (filter.hasDuration() && timedArgument.hasDuration()) {
				filterData = filter.asDocument().append("duration", timedArgument.getDuration().toSeconds());
			} else {
				filterData = filter.asDocument();
			}

			List<Bson> update = List.of(Operators.set("filters", Operators.let(new Document("filters", Operators.ifNull("$filters", Collections.EMPTY_LIST)), Operators.cond(Operators.isEmpty(Operators.filter("$$filters", Operators.eq("$$this.type", filter.getType()))), Operators.concatArrays("$$filters", List.of(filterData)), "$filters"))));
			event.getMongo().updateAutoRole(Filters.eq("roleId", role.getIdLong()), update, new UpdateOptions()).whenComplete((result, exception) -> {
				if (ExceptionUtility.sendExceptionally(event, exception)) {
					return;
				}

				if (result.getMatchedCount() == 0) {
					event.replyFailure("That role is not an auto role").queue();
					return;
				}

				if (result.getModifiedCount() == 0) {
					event.replyFailure("That auto role already has that filter or a contradicting filter").queue();
					return;
				}
				
				event.reply("That auto role now has the filter `" + filter.name() + "` " + (timedArgument.hasDuration() ? "with a duration of " + TimeUtility.getTimeString(timedArgument.getSeconds()) + " " : "") + event.getConfig().getSuccessEmote()).queue();
			});
		}
		
		@Command(value="remove", description="Removes a filter from an auto  role")
		@CommandId(44)
		@Examples({"auto role filter remove @Role BOT", "auto role filter remove Role NOT_BOT"})
		@AuthorPermissions(permissions={Permission.MANAGE_ROLES})
		public void remove(Sx4CommandEvent event, @Argument(value="role") Role role, @Argument(value="filter | all") @AlternativeOptions("all") Alternative<AutoRoleFilter> option) {
			boolean alternative = option.isAlternative();

			Bson update = alternative ? Updates.unset("filters") : Updates.pull("filters", Filters.eq("type", option.getValue().getType()));

			event.getMongo().updateAutoRole(Filters.eq("roleId", role.getIdLong()), update, new UpdateOptions()).whenComplete((result, exception) -> {
				if (ExceptionUtility.sendExceptionally(event, exception)) {
					return;
				}

				if (result.getMatchedCount() == 0) {
					event.replyFailure("That role is not an auto role").queue();
					return;
				}
				
				if (result.getModifiedCount() == 0) {
					event.reply("That auto role does not have " + (alternative ? "any" : "that") + " filter" + (alternative ? "s " : " ") + event.getConfig().getFailureEmote()).queue();
					return;
				}
				
				event.replySuccess((alternative ? "All" : "That") + " filter" + (alternative ? "s have" : " has") + " been removed from that auto role").queue();
			});
		}
		
		@Command(value="list", description="Lists all the filters you can use with descriptions")
		@CommandId(45)
		@Examples({"auto role filter list"})
		public void list(Sx4CommandEvent event) {
			StringBuilder description = new StringBuilder();
			
			Arrays.stream(AutoRoleFilter.values())
				.map(filter -> "`" + filter.name() + "` - " + filter.getDescription() + "\n\n")
				.forEach(description::append);
			
			EmbedBuilder embed = new EmbedBuilder()
				.setDescription(description.toString())
				.setAuthor("Filter List", null, event.getGuild().getIconUrl());
			
			event.reply(embed.build()).queue();
		}
		
	}
	
}
