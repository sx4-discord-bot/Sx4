package com.sx4.modules;

import static com.rethinkdb.RethinkDB.r;

import java.util.Map;

import com.jockie.bot.core.Context;
import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.command.Command;
import com.jockie.bot.core.command.Command.AuthorPermissions;
import com.jockie.bot.core.command.Command.BotPermissions;
import com.jockie.bot.core.command.ICommand.ContentOverflowPolicy;
import com.jockie.bot.core.command.Initialize;
import com.jockie.bot.core.command.impl.CommandEvent;
import com.jockie.bot.core.command.impl.CommandImpl;
import com.jockie.bot.core.module.Module;
import com.rethinkdb.gen.ast.Get;
import com.rethinkdb.model.OptArgs;
import com.rethinkdb.net.Connection;
import com.sx4.categories.Categories;
import com.sx4.core.Sx4Command;
import com.sx4.utils.ArgumentUtils;
import com.sx4.utils.HelpUtils;

import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.Member;
import net.dv8tion.jda.core.entities.Role;

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
		public void toggle(CommandEvent event, @Context Connection connection) {
			r.table("autorole").insert(r.hashMap("id", event.getGuild().getId()).with("role", null).with("botrole", null).with("toggle", false)).run(connection, OptArgs.of("durability", "soft"));
			Get data = r.table("autorole").get(event.getGuild().getId());
			Map<String, Object> dataRan = data.run(connection);
			
			if ((boolean) dataRan.get("toggle") == true) {
				event.reply("Auto role is now disabled <:done:403285928233402378>").queue();
				data.update(r.hashMap("toggle", false)).runNoReply(connection);
			} else {
				event.reply("Auto role is now enabled <:done:403285928233402378>").queue();
				data.update(r.hashMap("toggle", true)).runNoReply(connection);
			}
		}
		
		@Command(value="role", aliases={"user role", "userrole"}, description="Set the auto role, this role will be given to every user which joins the server if a bot role is not set otherwise it'll give it to ever non bot user who joins")
		@AuthorPermissions({Permission.MANAGE_ROLES})
		public void role(CommandEvent event, @Context Connection connection, @Argument(value="role", endless=true) String roleArgument) {
			r.table("autorole").insert(r.hashMap("id", event.getGuild().getId()).with("role", null).with("botrole", null).with("toggle", false)).run(connection, OptArgs.of("durability", "soft"));
			Get data = r.table("autorole").get(event.getGuild().getId());
			Map<String, Object> dataRan = data.run(connection);
			
			Role role = ArgumentUtils.getRole(event.getGuild(), roleArgument);
			if (role == null) {
				event.reply("I could not find that role :no_entry:").queue();
				return;
			}
			
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
			
			Role currentRole = null;
			String roleData = (String) dataRan.get("role");
			if (roleData != null) {
				currentRole = event.getGuild().getRoleById(roleData);
			}
			
			if (role.equals(currentRole)) {
				event.reply("The autorole role is already set to `" + role.getName() + "` :no_entry:").queue();
				return;
			}
			
			event.reply("The autorole role has been set to **" + role.getName() + "** <:done:403285928233402378>").queue();
			data.update(r.hashMap("role", role.getId())).runNoReply(connection);
		}
		
		@Command(value="bot role", aliases={"botrole"}, description="Set the bot role, this role will be given to every bot which joins the server")
		@AuthorPermissions({Permission.MANAGE_ROLES})
		public void botRole(CommandEvent event, @Context Connection connection, @Argument(value="role", endless=true) String roleArgument) {
			r.table("autorole").insert(r.hashMap("id", event.getGuild().getId()).with("role", null).with("botrole", null).with("toggle", false)).run(connection, OptArgs.of("durability", "soft"));
			Get data = r.table("autorole").get(event.getGuild().getId());
			Map<String, Object> dataRan = data.run(connection);
			
			Role role = ArgumentUtils.getRole(event.getGuild(), roleArgument);
			if (role == null) {
				event.reply("I could not find that role :no_entry:").queue();
				return;
			}
			
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
			
			Role currentRole = null;
			String roleData = (String) dataRan.get("botrole");
			if (roleData != null) {
				currentRole = event.getGuild().getRoleById(roleData);
			}
			
			if (role.equals(currentRole)) {
				event.reply("The autorole bot role is already set to `" + role.getName() + "` :no_entry:").queue();
				return;
			}
			
			event.reply("The autorole bot role has been set to **" + role.getName() + "** <:done:403285928233402378>").queue();
			data.update(r.hashMap("botrole", role.getId())).runNoReply(connection);
		}
		
		@Command(value="stats", aliases={"settings", "setting"}, description="View the current settings of auto role in this server", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
		public void stats(CommandEvent event, @Context Connection connection) {
			Map<String, Object> data = r.table("autorole").get(event.getGuild().getId()).run(connection);
			
			Role autoRole = null, botRole = null;
			if (data != null) {
				autoRole = (String) data.get("role") == null ? null : event.getGuild().getRoleById((String) data.get("role"));
				botRole = (String) data.get("botrole") == null ? null : event.getGuild().getRoleById((String) data.get("botrole"));
			}
			
			EmbedBuilder embed = new EmbedBuilder();
			embed.setAuthor("Auto Role Settings", null, event.getGuild().getIconUrl());
			embed.addField("Status", data == null ? "Disabled" : (boolean) data.get("toggle") == true ? "Enabled" : "Disabled", true); 
			embed.addField("Role", autoRole == null ? "Not Set" : autoRole.getAsMention(), true);
			embed.addField("Bot Role", botRole == null ? "Not Set" : botRole.getAsMention(), true);
			
			event.reply(embed.build()).queue();
		}
		
		@Command(value="fix", description="Allows you to give all the current members in your server the auto role", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@AuthorPermissions({Permission.MANAGE_ROLES})
		public void fix(CommandEvent event, @Context Connection connection) {
			Map<String, Object> data = r.table("autorole").get(event.getGuild().getId()).run(connection);
			
			String roleData = (String) data.get("role");
			String botRoleData = (String) data.get("botrole");
			
			Role role = null, botRole = null;
			if (roleData != null) {
				role = event.getGuild().getRoleById(roleData);
			} 
			
			if (botRoleData != null) {
				botRole = event.getGuild().getRoleById(botRoleData);
			}
			
			if (botRole == null && role == null) {
				event.reply("The auto role has not been set up in this server :no_entry:").queue();
				return;
			}
			
			int users = 0, bots = 0;
			for (Member member : event.getGuild().getMembers()) {
				if (roleData != null && botRoleData == null) {
					if (role != null) {
						event.getGuild().getController().addSingleRoleToMember(member, role).queue();
						users += 1;
					}
				} else if (roleData == null && botRoleData != null) {
					if (member.getUser().isBot()) {
						if (botRole != null) {
							event.getGuild().getController().addSingleRoleToMember(member, botRole).queue();
							bots += 1;
						}
					}
				} else {
					if (member.getUser().isBot()) {
						if (botRole != null) {
							event.getGuild().getController().addSingleRoleToMember(member, botRole).queue();
							bots += 1;
						}
					} else {
						if (role != null) {
							event.getGuild().getController().addSingleRoleToMember(member, role).queue();
							users += 1;
						}
					}
				}
			}
			
			if (users == 0 && bots == 0) {
				event.reply("The autorole is already applied to every member in the server :no_entry:").queue();
				return;
			}
			
			event.reply((users == 0 ? "" : "**" + users + "** users will be given the `" + role.getName() + "` role") + (bots != 0 && users != 0 ? " and " : "") + (bots == 0 ? "" : "**" + bots + "** bots will be given the `" + botRole.getName() + "` role") + " <:done:403285928233402378>").queue();
		}
		
	}
	
	@Initialize(all=true)
	public void initialize(CommandImpl command) {
		command.setCategory(Categories.AUTO_ROLE);
	}

}
