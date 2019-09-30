package com.sx4.bot.modules;

import java.util.List;

import org.bson.Document;
import org.bson.conversions.Bson;

import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.command.Command;
import com.jockie.bot.core.command.Command.AuthorPermissions;
import com.jockie.bot.core.command.Command.BotPermissions;
import com.jockie.bot.core.command.Context;
import com.jockie.bot.core.command.Initialize;
import com.jockie.bot.core.command.impl.CommandEvent;
import com.jockie.bot.core.command.impl.CommandImpl;
import com.jockie.bot.core.command.ICommand.ContentOverflowPolicy;
import com.jockie.bot.core.module.Module;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Updates;
import com.sx4.bot.categories.Categories;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEventListener;
import com.sx4.bot.database.Database;
import com.sx4.bot.utils.ArgumentUtils;
import com.sx4.bot.utils.HelpUtils;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;

@Module
public class AutoroleModule {
	
	public class AutoRoleCommand extends Sx4Command {
		
		public AutoRoleCommand() {
			super("auto role");
			
			super.setDescription("Set an auto role to be given to every new member that joins the server, you can also set a bot role to seperate bots and users in different roles");
			super.setAliases("autorole");
			super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
		}
		
		public void onCommand(CommandEvent event) {
			event.reply(HelpUtils.getHelpMessage(event.getCommand())).queue();
		}
		
		@Command(value="toggle", description="Enabled/disable autorole in the current server", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@AuthorPermissions({Permission.MANAGE_ROLES})
		public void toggle(CommandEvent event, @Context Database database) {
			boolean enabled = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("autorole.enabled")).getEmbedded(List.of("autorole", "enabled"), false);
			database.updateGuildById(event.getGuild().getIdLong(), Updates.set("autorole.enabled", !enabled), (result, exception) -> {
				if (exception != null) {
					exception.printStackTrace();
					event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
				} else {
					event.reply("Auto role is now " + (enabled ? "disabled" : "enabled") + " <:done:403285928233402378>").queue();
				}
			});
		}
		
		@Command(value="role", aliases={"user role", "userrole"}, description="Set the auto role, this role will be given to every user which joins the server if a bot role is not set otherwise it'll give it to ever non bot user who joins")
		@AuthorPermissions({Permission.MANAGE_ROLES})
		public void role(CommandEvent event, @Context Database database, @Argument(value="role", endless=true) String roleArgument) {
			Role role = null;
			if (!roleArgument.toLowerCase().equals("reset")) {
				role = ArgumentUtils.getRole(event.getGuild(), roleArgument);
				if (role == null) {
					event.reply("I could not find that role :no_entry:").queue();
					return;
				}
			}
			
			if (role != null) {
				if (role.isManaged()) {
					event.reply("I cannot give a role which is managed :no_entry:").queue();
					return;
				}
				
				if (role.isPublicRole()) {
					event.reply("I cannot give users the `@everyone` role :no_entry:").queue();
					return;
				}
				
				if (!event.getMember().canInteract(role)) {
					event.reply("You cannot set a role which is higher or equal than your top role :no_entry:").queue();
					return;
				}
				
				if (!event.getSelfMember().canInteract(role)) {
					event.reply("I cannot give a role which is higher or equal than my top role :no_entry:").queue();
					return;
				}
			}
			
			Long roleId = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("autorole.roleId")).getEmbedded(List.of("autorole", "roleId"), Long.class);
			Role currentRole = null;
			if (roleId != null) {
				currentRole = event.getGuild().getRoleById(roleId);
			}
			
			if (role != null && currentRole != null) {
				if (role.equals(currentRole)) {
					event.reply("The autorole role is already set to `" + role.getName() + "` :no_entry:").queue();
					return;
				}
			} else {
				if (role == null && currentRole == null) {
					event.reply("The autorole role is already unset :no_entry:").queue();
					return;
				}
			}
			
			String state = role == null ? "unset" : "set to **" + role.getName() + "**";
			Bson update = role == null ? Updates.unset("autorole.roleId") : Updates.set("autorole.roleId", role.getIdLong());
			database.updateGuildById(event.getGuild().getIdLong(), update, (result, exception) -> {
				if (exception != null) {
					exception.printStackTrace();
					event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
				} else {
					event.reply("The autorole role has been " + state + " <:done:403285928233402378>").queue();
				}
			});
		}
		
		@Command(value="bot role", aliases={"botrole"}, description="Set the bot role, this role will be given to every bot which joins the server")
		@AuthorPermissions({Permission.MANAGE_ROLES})
		public void botRole(CommandEvent event, @Context Database database, @Argument(value="role", endless=true) String roleArgument) {
			Role role = null;
			if (!roleArgument.toLowerCase().equals("reset")) {
				role = ArgumentUtils.getRole(event.getGuild(), roleArgument);
				if (role == null) {
					event.reply("I could not find that role :no_entry:").queue();
					return;
				}
			}
			
			if (role != null) {
				if (role.isManaged()) {
					event.reply("I cannot give a role which is managed :no_entry:").queue();
					return;
				}
				
				if (role.isPublicRole()) {
					event.reply("I cannot give users the `@everyone` role :no_entry:").queue();
					return;
				}
				
				if (!event.getMember().canInteract(role)) {
					event.reply("You cannot set a role which is higher or equal than your top role :no_entry:").queue();
					return;
				}
				
				if (!event.getSelfMember().canInteract(role)) {
					event.reply("I cannot give a role which is higher or equal than my top role :no_entry:").queue();
					return;
				}
			}
			
			Long roleId = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("autorole.botRoleId")).getEmbedded(List.of("autorole", "botRoleId"), Long.class);
			Role currentRole = null;
			if (roleId != null) {
				currentRole = event.getGuild().getRoleById(roleId);
			}
			
			if (role != null && currentRole != null) {
				if (role.equals(currentRole)) {
					event.reply("The autorole bot role is already set to `" + role.getName() + "` :no_entry:").queue();
					return;
				}
			} else {
				if (role == null && currentRole == null) {
					event.reply("The autorole bot role is already unset :no_entry:").queue();
					return;
				}
			}
			
			String state = role == null ? "unset" : "set to **" + role.getName() + "**";
			Bson update = role == null ? Updates.unset("autorole.botRoleId") : Updates.set("autorole.botRoleId", role.getIdLong());
			database.updateGuildById(event.getGuild().getIdLong(), update, (result, exception) -> {
				if (exception != null) {
					exception.printStackTrace();
					event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
				} else {
					event.reply("The autorole bot role has been " + state + " <:done:403285928233402378>").queue();
				}
			});
		}
		
		@Command(value="stats", aliases={"settings", "setting"}, description="View the current settings of auto role in this server", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
		public void stats(CommandEvent event, @Context Database database) {
			Document data = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("autorole.enabled", "autorole.roleId", "autorole.botRoleId", "autorole.autoUpdate")).get("autorole", Database.EMPTY_DOCUMENT);
			
			Long autoRoleId = data.getLong("roleId"), botRoleId = data.getLong("botRoleId");
			Role autoRole = autoRoleId == null ? null : event.getGuild().getRoleById(autoRoleId);
			Role botRole = botRoleId == null ? null : event.getGuild().getRoleById(botRoleId);
			
			EmbedBuilder embed = new EmbedBuilder();
			embed.setAuthor("Auto Role Settings", null, event.getGuild().getIconUrl());
			embed.addField("Status", data.getBoolean("enabled", false) ? "Enabled" : "Disabled", true); 
			embed.addField("Auto Update", data.getBoolean("autoUpdate", true) ? "Enabled" : "Disabled", true);
			embed.addField("Role", autoRole == null ? "Not Set" : autoRole.getAsMention(), true);
			embed.addField("Bot Role", botRole == null ? "Not Set" : botRole.getAsMention(), true);
			
			event.reply(embed.build()).queue();
		}
		
		@Command(value="auto update", aliases={"toggle auto update", "autoupdate", "toggle autoupdate"}, description="Enables/disables whether the bot should give members the autorole when it comes online in case it missed anyone while it was offline", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@AuthorPermissions({Permission.MANAGE_ROLES})
		public void autoUpdate(CommandEvent event, @Context Database database) {
			boolean enabled = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("autorole.autoUpdate")).getEmbedded(List.of("autorole", "autoUpdate"), false);
			database.updateGuildById(event.getGuild().getIdLong(), Updates.set("autorole.autoUpdate", !enabled), (result, exception) -> {
				if (exception != null) {
					exception.printStackTrace();
					event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
				} else {
					event.reply("Auto updating for auto role is now " + (enabled ? "disabled" : "enabled") + " <:done:403285928233402378>").queue();
				}
			});
		}
		
		@Command(value="fix", description="Allows you to give all the current members in your server the auto role", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@AuthorPermissions({Permission.MANAGE_ROLES})
		@BotPermissions({Permission.MANAGE_ROLES})
		public void fix(CommandEvent event, @Context Database database) {
			Document data = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("autorole.roleId", "autorole.botRoleId")).get("autorole", Database.EMPTY_DOCUMENT);
			
			Long roleId = data.getLong("roleId");
			Long botRoleId = data.getLong("botRoleId");
			
			Role role = roleId == null ? null : event.getGuild().getRoleById(roleId);
			Role botRole = botRoleId == null ? null : event.getGuild().getRoleById(botRoleId);
			if (botRole == null && role == null) {
				event.reply("The auto role has not been set up in this server :no_entry:").queue();
				return;
			}
			
			if ((role != null && !event.getGuild().getSelfMember().canInteract(role)) || (botRole != null && !event.getGuild().getSelfMember().canInteract(botRole))) {
				event.reply("The autorole or botrole is above my highest role so I cannot give it to any users :no_entry:").queue();
				return;
			}
			
			int users = 0, bots = 0;
			for (Member member : event.getGuild().getMembers()) {
				if (roleId != null && botRoleId == null) {
					if (role != null && !member.getRoles().contains(role)) {
						event.getGuild().addRoleToMember(member, role).queue();
						users++;
					}
				} else if (roleId == null && botRoleId != null) {
					if (member.getUser().isBot()) {
						if (botRole != null && !member.getRoles().contains(botRole)) {
							event.getGuild().addRoleToMember(member, botRole).queue();
							bots++;
						}
					}
				} else {
					if (member.getUser().isBot()) {
						if (botRole != null && !member.getRoles().contains(botRole)) {
							event.getGuild().addRoleToMember(member, botRole).queue();
							bots++;
						}
					} else {
						if (role != null && !member.getRoles().contains(role)) {
							event.getGuild().addRoleToMember(member, role).queue();
							users++;
						}
					}
				}
			}
			
			if (users == 0 && bots == 0) {
				event.reply("The autorole is already applied to every user in the server :no_entry:").queue();
				return;
			}
			
			event.reply((users == 0 ? "" : "**" + users + "** user" + (users == 1 ? "" : "s") +  " will be given the `" + role.getName() + "` role") + (bots != 0 && users != 0 ? " and " : "") + (bots == 0 ? "" : "**" + bots + "** bot" + (bots == 1 ? "" : "s") + " will be given the `" + botRole.getName() + "` role") + " <:done:403285928233402378>").queue();
		}
		
	}
	
	@Initialize(all=true)
	public void initialize(CommandImpl command) {
		command.setCategory(Categories.AUTO_ROLE);
	}

}
