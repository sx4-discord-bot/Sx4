package com.sx4.bot.commands.management;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.bson.conversions.Bson;

import com.google.common.base.Predicates;
import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.command.Command;
import com.jockie.bot.core.command.Context;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Updates;
import com.sx4.bot.annotations.command.AuthorPermissions;
import com.sx4.bot.annotations.command.Examples;
import com.sx4.bot.category.Category;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEvent;
import com.sx4.bot.database.model.Operators;
import com.sx4.bot.entities.argument.All;
import com.sx4.bot.paged.PagedResult;
import com.sx4.bot.utility.ExceptionUtility;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;

public class AutoRoleCommand extends Sx4Command {

	public AutoRoleCommand() {
		super("auto role");
		
		super.setDescription("Sets roles to be given when a user joins the server");
		super.setAliases("autorole");
		super.setExamples("auto role toggle", "auto role add", "auto role remove");
		super.setCategory(Category.MANAGEMENT);
	}
	
	public void onCommand(Sx4CommandEvent event) {
		event.replyHelp().queue();
	}
	
	@Command(value="toggle", description="Toggles whether auto role is enabled or discord in this server")
	@Examples({"auto role toggle"})
	@AuthorPermissions(permissions={Permission.MANAGE_ROLES})
	public void toggle(Sx4CommandEvent event) {
		List<Bson> update = List.of(Operators.set("autoRole.enabled", Operators.cond("$autoRole.enabled", Operators.REMOVE, true)));
		this.database.findAndUpdateGuildById(event.getGuild().getIdLong(), Projections.include("autoRole.enabled"), update).whenComplete((data, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}
			
			event.reply("Auto role is now **" + (data.getEmbedded(List.of("autoRole", "enabled"), false) ? "enabled" : "disabled") + "** " + this.config.getSuccessEmote()).queue();
		});
	}
	
	@Command(value="add", description="Add a role to be given when a user joins")
	@Examples({"auto role add @Role", "auto role add Role", "auto role add 406240455622262784"})
	@AuthorPermissions(permissions={Permission.MANAGE_ROLES})
	public void add(Sx4CommandEvent event, @Argument(value="role", endless=true) Role role) {
		if (role.isManaged()) {
			event.reply("I cannot give a managed role " + this.config.getFailureEmote()).queue();
			return;
		}
		
		if (role.isPublicRole()) {
			event.reply("I cannot give the `@everyone` role " + this.config.getFailureEmote()).queue();
			return;
		}
		
		if (!event.getSelfMember().canInteract(role)) {
			event.reply("I cannot give a role higher or equal than my top role " + this.config.getFailureEmote()).queue();
			return;
		}
		
		if (!event.getMember().canInteract(role)) {
			event.reply("You cannot give a role higher or equal than your top role " + this.config.getFailureEmote()).queue();
			return;
		}
		
		this.database.updateGuildById(event.getGuild().getIdLong(), Updates.addToSet("autoRole.roles", role.getIdLong())).whenComplete((result, exception) -> {
			if (ExceptionUtility.sendExceptionally(event, exception)) {
				return;
			}
			
			if (result.getModifiedCount() == 0) {
				event.reply("That role is already an auto role " + this.config.getFailureEmote()).queue();
				return;
			}
			
			event.reply("The role " + role.getAsMention() + " has been added as an auto role " + this.config.getSuccessEmote()).queue();
		});
	}
	
	@Command(value="remove", description="Remove a role from being given when a user joins")
	@Examples({"auto role remove @Role", "auto role remove Role", "auto role remove all"})
	@AuthorPermissions(permissions={Permission.MANAGE_ROLES})
	public void remove(Sx4CommandEvent event, @Argument(value="role", endless=true) All<Role> allArgument) {
		if (allArgument.isAll()) {
			this.database.updateGuildById(event.getGuild().getIdLong(), Updates.unset("autoRole.roles")).whenComplete((result, exception) -> {
				if (ExceptionUtility.sendExceptionally(event, exception)) {
					return;
				}
				
				if (result.getModifiedCount() == 0) {
					event.reply("You have no auto roles setup " + this.config.getFailureEmote()).queue();
					return;
				}
				
				event.reply("All auto roles have been removed " + this.config.getSuccessEmote()).queue();
			});
		} else {
			Role role = allArgument.getValue();
			this.database.updateGuildById(event.getGuild().getIdLong(), Updates.pull("autoRole.roles", role.getIdLong())).whenComplete((result, exception) -> {
				if (ExceptionUtility.sendExceptionally(event, exception)) {
					return;
				}
				
				if (result.getModifiedCount() == 0) {
					event.reply("That role is not an auto role " + this.config.getFailureEmote()).queue();
					return;
				}
				
				event.reply("The role " + role.getAsMention() + " has been removed from being an auto role " + this.config.getSuccessEmote()).queue();
			});
		}
	}
	
	@Command(value="list", description="Lists all the auto roles setup")
	@Examples({"auto role list"})
	public void list(Sx4CommandEvent event, @Context Guild guild) {
		List<Long> roleIds = this.database.getGuildById(event.getGuild().getIdLong(), Projections.include("autoRole.roles")).getEmbedded(List.of("autoRole", "roles"), Collections.emptyList());
		if (roleIds.isEmpty()) {
			event.reply("You have no auto roles setup " + this.config.getFailureEmote()).queue();
			return;
		}
		
		List<Role> roles = roleIds.stream()
			.map(guild::getRoleById)
			.filter(Predicates.notNull())
			.collect(Collectors.toList());
		
		PagedResult<Role> paged = new PagedResult<>(roles)
			.setAuthor("Auto Roles", null, guild.getIconUrl())
			.setIndexed(false)
			.setDisplayFunction(Role::getAsMention);
		
		paged.execute(event);
	}
	
}
