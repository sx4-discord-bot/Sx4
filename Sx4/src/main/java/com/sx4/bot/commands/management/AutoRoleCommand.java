package com.sx4.bot.commands.management;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.command.Command;
import com.jockie.bot.core.command.Context;
import com.mongodb.MongoWriteException;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.sx4.bot.annotations.argument.Options;
import com.sx4.bot.annotations.command.AuthorPermissions;
import com.sx4.bot.annotations.command.CommandId;
import com.sx4.bot.annotations.command.Examples;
import com.sx4.bot.category.ModuleCategory;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.database.model.Operators;
import com.sx4.bot.entities.argument.Alternative;
import com.sx4.bot.entities.argument.TimedArgument;
import com.sx4.bot.entities.management.AutoRoleFilter;
import com.sx4.bot.paged.PagedResult;
import com.sx4.bot.utility.ExceptionUtility;
import com.sx4.bot.utility.TimeUtility;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
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
	
	@Command(value="toggle", description="Enables/disables auto role in this server")
	@CommandId(38)
	@Examples({"auto role toggle"})
	@AuthorPermissions(permissions={Permission.MANAGE_ROLES})
	public void toggle(Sx4CommandEvent event) {
		List<Bson> update = List.of(Operators.set("autoRole.enabled", Operators.cond("$autoRole.enabled", Operators.REMOVE, true)));
		this.database.findAndUpdateGuildById(event.getGuild().getIdLong(), Projections.include("autoRole.enabled"), update).whenComplete((data, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}
			
			event.replySuccess("Auto role is now **" + (data.getEmbedded(List.of("autoRole", "enabled"), false) ? "enabled" : "disabled") + "**").queue();
		});
	}
	
	@Command(value="add", description="Add a role to be given when a user joins")
	@CommandId(39)
	@Examples({"auto role add @Role", "auto role add Role", "auto role add 406240455622262784"})
	@AuthorPermissions(permissions={Permission.MANAGE_ROLES})
	public void add(Sx4CommandEvent event, @Argument(value="role", endless=true) Role role) {
		if (role.isManaged()) {
			event.replyFailure("I cannot give a managed role").queue();
			return;
		}
		
		if (role.isPublicRole()) {
			event.replyFailure("I cannot give the `@everyone` role").queue();
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
		
		Document data = new Document("id", role.getIdLong());
		
		List<Bson> update = List.of(Operators.set("autoRole.roles", Operators.cond(Operators.or(Operators.extinct("$autoRole.roles"), Operators.eq(Operators.filter("$autoRole.roles", Operators.eq("$$this.id", role.getIdLong())), Collections.EMPTY_LIST)), Operators.cond(Operators.exists("$autoRole.roles"), Operators.concatArrays("$autoRole.roles", List.of(data)), List.of(data)), "$autoRole.roles")));
		this.database.updateGuildById(event.getGuild().getIdLong(), update).whenComplete((result, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}
			
			if (result.getModifiedCount() == 0) {
				event.replyFailure("That role is already an auto role").queue();
				return;
			}
			
			event.replySuccess("The role " + role.getAsMention() + " has been added as an auto role").queue();
		});
	}
	
	@Command(value="remove", description="Remove a role from being given when a user joins")
	@CommandId(40)
	@Examples({"auto role remove @Role", "auto role remove Role", "auto role remove all"})
	@AuthorPermissions(permissions={Permission.MANAGE_ROLES})
	public void remove(Sx4CommandEvent event, @Argument(value="role | all", endless=true) @Options("all") Alternative<Role> option) {
		if (option.isAlternative()) {
			this.database.updateGuildById(event.getGuild().getIdLong(), Updates.unset("autoRole.roles")).whenComplete((result, exception) -> {
				if (ExceptionUtility.sendExceptionally(event, exception)) {
					return;
				}
				
				if (result.getModifiedCount() == 0) {
					event.replyFailure("You have no auto roles setup").queue();
					return;
				}
				
				event.replySuccess("All auto roles have been removed").queue();
			});
		} else {
			Role role = option.getValue();
			this.database.updateGuildById(event.getGuild().getIdLong(), Updates.pull("autoRole.roles", Filters.eq("id", role.getIdLong()))).whenComplete((result, exception) -> {
				if (ExceptionUtility.sendExceptionally(event, exception)) {
					return;
				}
				
				if (result.getModifiedCount() == 0) {
					event.replyFailure("That role is not an auto role").queue();
					return;
				}
				
				event.replySuccess("The role " + role.getAsMention() + " has been removed from being an auto role").queue();
			});
		}
	}
	
	@Command(value="list", description="Lists all the auto roles setup")
	@CommandId(41)
	@Examples({"auto role list"})
	public void list(Sx4CommandEvent event, @Context Guild guild) {
		List<Long> roleIds = this.database.getGuildById(event.getGuild().getIdLong(), Projections.include("autoRole.roles")).getEmbedded(List.of("autoRole", "roles"), Collections.emptyList());
		if (roleIds.isEmpty()) {
			event.replyFailure("You have no auto roles setup").queue();
			return;
		}
		
		List<Role> roles = roleIds.stream()
			.map(guild::getRoleById)
			.filter(Objects::nonNull)
			.collect(Collectors.toList());
		
		PagedResult<Role> paged = new PagedResult<>(roles)
			.setAuthor("Auto Roles", null, guild.getIconUrl())
			.setIndexed(false)
			.setDisplayFunction(Role::getAsMention);
		
		paged.execute(event);
	}
	
	public class FilterCommand extends Sx4Command {
		
		public FilterCommand() {
			super("filter", 42);
			
			super.setDescription("Add or remove filters from auto roles");
			super.setExamples("auto role filter add", "auto role filter remove", "auto role filter list");
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
			
			List<Document> roles = this.database.getGuildById(event.getGuild().getIdLong(), Projections.include("autoRole.roles")).getEmbedded(List.of("autoRole", "roles"), Collections.emptyList());
			Document roleData = roles.stream()
				.filter(data -> data.getLong("id") == role.getIdLong())
				.findFirst()
				.orElse(null);
			
			if (roleData == null) {
				event.replyFailure("That role is not an auto role").queue();
				return;
			}
			
			List<Document> filters = roleData.getList("filters", Document.class, Collections.emptyList());
			if (filters.stream().anyMatch(data -> data.getString("key").equals(filter.getKey()))) {
				event.replyFailure("That auto role already has that filter or has a contradicting filter").queue();
				return;
			}
			
			Document filterData;
			if (filter.hasDuration() && timedArgument.hasDuration()) {
				filterData = filter.asDocument().append("duration", timedArgument.getDuration().toSeconds());
			} else {
				filterData = filter.asDocument();
			}
			
			UpdateOptions options = new UpdateOptions().arrayFilters(List.of(Filters.eq("role.id", role.getIdLong())));
			this.database.updateGuildById(event.getGuild().getIdLong(), Updates.push("autoRole.roles.$[role].filters", filterData), options).whenComplete((result, exception) -> {
				if (ExceptionUtility.sendExceptionally(event, exception)) {
					return;
				}
				
				event.reply("That auto role now has the filter `" + filter.name() + "` " + (timedArgument.hasDuration() ? "with a duration of " + TimeUtility.getTimeString(timedArgument.getSeconds()) + " " : "") + this.config.getSuccessEmote()).queue();
			});
		}
		
		@Command(value="remove", description="Removes a filter from an auto  role")
		@CommandId(44)
		@Examples({"auto role filter remove @Role BOT", "auto role filter remove Role NOT_BOT"})
		@AuthorPermissions(permissions={Permission.MANAGE_ROLES})
		public void remove(Sx4CommandEvent event, @Argument(value="role | all") Role role, @Argument(value="filter") @Options("all") Alternative<AutoRoleFilter> option) {
			boolean alternative = option.isAlternative();
			
			UpdateOptions options = new UpdateOptions().arrayFilters(List.of(Filters.eq("role.id", role.getIdLong())));
			Bson update = alternative ? Updates.unset("autoRole.roles.$[role].filters") : Updates.pull("autoRole.roles.$[role].filters", Filters.eq("key", option.getValue().getKey()));
			this.database.updateGuildById(event.getGuild().getIdLong(), update, options).whenComplete((result, exception) -> {
				Throwable cause = exception == null ? null : exception.getCause();
				if (cause instanceof MongoWriteException && ((MongoWriteException) cause).getCode() == 2) {
					event.reply("That auto role does not have " + (alternative ? "any" : "that") + " filter" + (alternative ? "s " : " ") + this.config.getFailureEmote()).queue();
					return;
				}
				
				if (ExceptionUtility.sendExceptionally(event, exception)) {
					return;
				}
				
				if (result.getModifiedCount() == 0) {
					event.reply("That auto role does not have " + (alternative ? "any" : "that") + " filter" + (alternative ? "s " : " ") + this.config.getFailureEmote()).queue();
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
