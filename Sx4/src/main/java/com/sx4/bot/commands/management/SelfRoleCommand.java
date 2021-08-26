package com.sx4.bot.commands.management;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.command.Command;
import com.mongodb.ErrorCategory;
import com.mongodb.MongoWriteException;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.sx4.bot.annotations.argument.AlternativeOptions;
import com.sx4.bot.annotations.command.AuthorPermissions;
import com.sx4.bot.annotations.command.CommandId;
import com.sx4.bot.annotations.command.Examples;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.entities.argument.Alternative;
import com.sx4.bot.paged.PagedResult;
import com.sx4.bot.utility.ExceptionUtility;
import com.sx4.bot.waiter.Waiter;
import com.sx4.bot.waiter.exception.CancelException;
import com.sx4.bot.waiter.exception.TimeoutException;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.interactions.components.Button;
import net.dv8tion.jda.api.requests.RestAction;
import org.bson.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;

public class SelfRoleCommand extends Sx4Command {

	public SelfRoleCommand() {
		super("self role", 331);

		super.setDescription("Give or remove a self role from yourself");
		super.setAliases("self roles", "selfrole", "selfroles");
		super.setExamples("self role @Role", "self role Role", "self role 330400064541425664");
		super.setCategoryAll(ModuleCategory.MANAGEMENT);
	}

	public void onCommand(Sx4CommandEvent event, @Argument(value="role", endless=true) Role role) {
		Document selfRole = event.getMongo().getSelfRole(Filters.eq("roleId", role.getIdLong()), Projections.include("_id"));
		if (selfRole == null) {
			event.replyFailure("That role is not a self role").queue();
			return;
		}

		boolean hasRole = event.getMember().getRoles().contains(role);

		RestAction<Void> action;
		if (hasRole) {
			action = event.getGuild().removeRoleFromMember(event.getMember(), role);
		} else {
			action = event.getGuild().addRoleToMember(event.getMember(), role);
		}

		action.flatMap($ -> event.replySuccess("You " + (hasRole ? "no longer" : "now") + " have " + role.getAsMention())).queue();
	}

	@Command(value="add", description="Add a self role that other users can give themselves to the current server")
	@CommandId(332)
	@Examples({"self role add @Role", "self role add Role", "self role add 330400064541425664"})
	@AuthorPermissions(permissions={Permission.MANAGE_ROLES})
	public void add(Sx4CommandEvent event, @Argument(value="role", endless=true) Role role) {
		if (role.isPublicRole()) {
			event.replyFailure("You cannot give the @everyone role as a self role").queue();
			return;
		}

		if (role.isManaged()) {
			event.replyFailure("You cannot add a managed role as a self role").queue();
			return;
		}

		if (!event.getSelfMember().canInteract(role)) {
			event.replyFailure("You cannot add a self role higher or equal than my top role").queue();
			return;
		}

		if (!event.getMember().canInteract(role)) {
			event.replyFailure("You cannot add a self role higher or equal than your top role").queue();
			return;
		}

		Document data = new Document("roleId", role.getIdLong())
			.append("guildId", event.getGuild().getIdLong());

		event.getMongo().insertSelfRole(data).whenComplete((result, exception) -> {
			Throwable cause = exception instanceof CompletionException ? exception.getCause() : exception;
			if (cause instanceof MongoWriteException && ((MongoWriteException) cause).getError().getCategory() == ErrorCategory.DUPLICATE_KEY) {
				event.replyFailure("That role is already a self role").queue();
				return;
			}

			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}

			event.replySuccess(role.getAsMention() + " is now a self role").queue();
		});
	}

	@Command(value="delete", aliases={"remove"}, description="Delete a self role from the current server")
	@CommandId(333)
	@Examples({"self role delete @Role", "self role delete Role", "self role delete all"})
	@AuthorPermissions(permissions={Permission.MANAGE_ROLES})
	public void remove(Sx4CommandEvent event, @Argument(value="role | all", endless=true) @AlternativeOptions("all") Alternative<Role> option) {
		if (option.isAlternative()) {
			List<Button> buttons = List.of(Button.success("yes", "Yes"), Button.danger("no", "No"));

			event.reply(event.getAuthor().getName() + ", are you sure you want to delete every self role in the server?").setActionRow(buttons).submit()
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
						.setRunAfter(e -> e.deferEdit().queue())
						.setTimeout(60)
						.start();
				}).thenCompose(e -> event.getMongo().deleteManySelfRoles(Filters.eq("guildId", event.getGuild().getIdLong())))
				.whenComplete((result, exception) -> {
					Throwable cause = exception instanceof CompletionException ? exception.getCause() : exception;
					if (cause instanceof CancelException) {
						event.replySuccess("Cancelled").queue();
						return;
					} else if (cause instanceof TimeoutException) {
						event.reply("Timed out :stopwatch:").queue();
						return;
					} else if (ExceptionUtility.sendExceptionally(event, exception)) {
						return;
					}

					if (result.getDeletedCount() == 0) {
						event.replyFailure("There are no self roles in this server").queue();
						return;
					}

					event.replySuccess("All self roles have been deleted").queue();
				});
		} else {
			Role role =option.getValue();

			event.getMongo().deleteSelfRole(Filters.eq("roleId", role.getIdLong())).whenComplete((result, exception) -> {
				if (ExceptionUtility.sendExceptionally(event, exception)) {
					return;
				}

				event.replySuccess(role.getAsMention() + " is no longer a self role").queue();
			});
		}
	}

	@Command(value="list", description="Lists all the self roles in the current server")
	@CommandId(334)
	@Examples({"self role list"})
	public void list(Sx4CommandEvent event) {
		List<Document> data = event.getMongo().getSelfRoles(Filters.eq("guildId", event.getGuild().getIdLong()), Projections.include("roleId")).into(new ArrayList<>());
		if (data.isEmpty()) {
			event.replyFailure("There are no self roles in this server").queue();
			return;
		}

		List<Role> roles = data.stream()
			.map(d -> d.getLong("roleId"))
			.map(event.getGuild()::getRoleById)
			.filter(Objects::nonNull)
			.collect(Collectors.toList());

		PagedResult<Role> paged = new PagedResult<>(event.getBot(), roles)
			.setAuthor("Self Roles", null, event.getGuild().getIconUrl())
			.setIndexed(false)
			.setSelect()
			.setDisplayFunction(Role::getAsMention);

		paged.execute(event);
	}

}
