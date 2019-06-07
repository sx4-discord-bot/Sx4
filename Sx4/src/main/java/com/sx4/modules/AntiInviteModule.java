package com.sx4.modules;

import static com.rethinkdb.RethinkDB.r;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.jockie.bot.core.Context;
import com.jockie.bot.core.argument.Argument;
import com.jockie.bot.core.command.Command;
import com.jockie.bot.core.command.Command.AuthorPermissions;
import com.jockie.bot.core.command.Command.BotPermissions;
import com.jockie.bot.core.command.Initialize;
import com.jockie.bot.core.command.impl.CommandEvent;
import com.jockie.bot.core.command.impl.CommandImpl;
import com.jockie.bot.core.module.Module;
import com.rethinkdb.gen.ast.Get;
import com.rethinkdb.model.OptArgs;
import com.rethinkdb.net.Connection;
import com.sx4.categories.Categories;
import com.sx4.core.Sx4Command;
import com.sx4.settings.Settings;
import com.sx4.utils.AntiInviteUtils;
import com.sx4.utils.ArgumentUtils;
import com.sx4.utils.GeneralUtils;
import com.sx4.utils.HelpUtils;
import com.sx4.utils.PagedUtils;
import com.sx4.utils.PagedUtils.PagedResult;

import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.Category;
import net.dv8tion.jda.core.entities.Channel;
import net.dv8tion.jda.core.entities.Member;
import net.dv8tion.jda.core.entities.Role;
import net.dv8tion.jda.core.entities.TextChannel;

@Module
public class AntiInviteModule {

	public class AntiInviteCommand extends Sx4Command {
		
		private final List<String> actions = List.of("mute", "kick", "ban");
		private final List<String> nullStrings = List.of("null", "none", "off", "reset");
		
		public AntiInviteCommand() {
			super("antiinvite");
			
			super.setAliases("anti invite", "anti-invite", "antiinv", "anti inv", "anti-inv");
			super.setDescription("Set up antiinvite to automatically delete any active discord invites which are sent in the server");
			super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
		}
		
		public void onCommand(CommandEvent event) {
			event.reply(HelpUtils.getHelpMessage(event.getCommand())).queue();
		}
		
		@Command(value="toggle", aliases={"enable", "disable"}, description="Enable/disable anti-invite for the current server", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@AuthorPermissions({Permission.MESSAGE_MANAGE})
		public void toggle(CommandEvent event, @Context Connection connection) {
			AntiInviteUtils.insertData(event.getGuild()).run(connection, OptArgs.of("durability", "soft"));
			Get data = r.table("antiad").get(event.getGuild().getId());
			Map<String, Object> dataRan = data.run(connection);
			
			if ((boolean) dataRan.get("toggle") == false) {
				event.reply("Anti-Invite is now enabled <:done:403285928233402378>").queue();
				data.update(r.hashMap("toggle", true)).runNoReply(connection);
			} else {
				event.reply("Anti-Invite is now disabled <:done:403285928233402378>").queue();
				data.update(r.hashMap("toggle", false)).runNoReply(connection);
			}
		}
		
		@Command(value="ban names", aliases={"ban user names", "banusernames", "bannames"}, description="Enable/disable whether the bot should ban users who join with an invite in their name", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@AuthorPermissions({Permission.BAN_MEMBERS})
		public void banNames(CommandEvent event, @Context Connection connection) {
			AntiInviteUtils.insertData(event.getGuild()).run(connection, OptArgs.of("durability", "soft"));
			Get data = r.table("antiad").get(event.getGuild().getId());
			Map<String, Object> dataRan = data.run(connection);
			
			if ((boolean) dataRan.get("baninvites") == false) {
				event.reply("Users who join with invites in their names will now be banned <:done:403285928233402378>").queue();
				data.update(r.hashMap("baninvites", true)).runNoReply(connection);
			} else {
				event.reply("Users who join with invites in their names will no longer be banned <:done:403285928233402378>").queue();
				data.update(r.hashMap("baninvites", false)).runNoReply(connection);
			}
		}
		
		@Command(value="action", aliases={"set action", "setaction"}, description="Set the action which will happen when a user posts a certain amount of invites (Set with antiinvite attempts), use off as an argument to turn this feature off", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@AuthorPermissions({Permission.BAN_MEMBERS})
		public void action(CommandEvent event, @Context Connection connection, @Argument(value="action") String action) {
			action = action.toLowerCase();
			if (nullStrings.contains(action)) {
				Get data = r.table("antiad").get(event.getGuild().getId());
				Map<String, Object> dataRan = data.run(connection);
				
				if (dataRan == null || dataRan.get("action") == null) {
					event.reply("You don't have an action set :no_entry:").queue();
					return;
				}
				
				long attempts = (long) dataRan.get("attempts");
				event.reply("An action will no longer occur when a user sends " + attempts + " invite" + (attempts == 1 ? "" : "s") + " <:done:403285928233402378>").queue();
				data.update(r.hashMap("action", null)).runNoReply(connection);
			} else if (actions.contains(action)) {
				AntiInviteUtils.insertData(event.getGuild()).run(connection, OptArgs.of("durability", "soft"));
				Get data = r.table("antiad").get(event.getGuild().getId());
				Map<String, Object> dataRan = data.run(connection);
				
				if (dataRan != null && dataRan.get("action") != null && dataRan.get("action").equals(action)) {
					event.reply("The action is already set to `" + action + "` :no_entry:").queue();
					return;
				}
				
				long attempts = (long) dataRan.get("attempts");
				event.reply("Users will now receive a `" + action + "` when sending **" + attempts + "** invite" + (attempts == 1 ? "" : "s") + " <:done:403285928233402378>").queue();
				data.update(r.hashMap("action", action)).runNoReply(connection);
			} else {
				event.reply("Invalid action, `" + GeneralUtils.joinGrammatical(actions) + "` are the valid actions :no_entry:").queue();
			}
		}
		
		@Command(value="attempts", description="Set the amount of times a user can send an invite before an action occurs to them set through `antiinvite action`", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@AuthorPermissions({Permission.MESSAGE_MANAGE})
		public void attempts(CommandEvent event, @Context Connection connection, @Argument(value="attempts") int attempts) {
			if (attempts < 1) {
				event.reply("The attempts cannot be any lower than 1 :no_entry:").queue();
				return;
			}
			
			AntiInviteUtils.insertData(event.getGuild()).run(connection, OptArgs.of("durability", "soft"));
			Get data = r.table("antiad").get(event.getGuild().getId());
			Map<String, Object> dataRan = data.run(connection);
			
			if ((long) dataRan.get("attempts") == attempts) {
				event.reply("Attempts is already set to **" + attempts + "** :no_entry:").queue();
				return;
			}
			
			event.reply("When a user sends **" + attempts + "** invite" + (attempts == 1 ? "" : "s") + " an action will occur to them <:done:403285928233402378>").queue();
			data.update(r.hashMap("attempts", attempts)).runNoReply(connection);
		}
		
		@SuppressWarnings("unchecked")
		@Command(value="reset attempts", aliases={"resetattempts"}, description="Resets a users attempts of sending invites to 0", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@AuthorPermissions({Permission.MESSAGE_MANAGE})
		public void resetAttempts(CommandEvent event, @Context Connection connection, @Argument(value="user", endless=true) String userArgument) {
			Member member = ArgumentUtils.getMember(event.getGuild(), userArgument);
			if (member == null) {
				event.reply("I could not find that user :no_entry:").queue();
				return;
			}
			
			Get data = r.table("antiad").get(event.getGuild().getId());
			Map<String, Object> dataRan = data.run(connection);
			
			if (dataRan == null) {
				event.reply("**" + member.getUser().getAsTag() + "** does not have any attempts :no_entry:").queue();
				return;
			}
			
			List<Map<String, Object>> users = (List<Map<String, Object>>) dataRan.get("users");
			if (users.isEmpty()) {
				event.reply("**" + member.getUser().getAsTag() + "** does not have any attempts :no_entry:").queue();
				return;
			}
			
			for (Map<String, Object> userData : users) {
				if (userData.get("id").equals(member.getUser().getId())) {
					event.reply("Attempts for **" + member.getUser().getAsTag() + "** have been reset <:done:403285928233402378>").queue();
						
					users.remove(userData);
					data.update(r.hashMap("users", users)).runNoReply(connection);
					return;
				}
			}
			
			event.reply("**" + member.getUser().getAsTag() + "** does not have any attempts :no_entry:").queue();
		}
		
		@SuppressWarnings("unchecked")
		@Command(value="whitelist", description="Whitelists a user/channel/role to be able to send invites")
		@AuthorPermissions({Permission.MANAGE_SERVER})
		public void whitelist(CommandEvent event, @Context Connection connection, @Argument(value="user | role | channel", endless=true) String argument) {
			Member member = ArgumentUtils.getMember(event.getGuild(), argument);
			Channel channel = ArgumentUtils.getTextChannelOrParent(event.getGuild(), argument);
			Role role = ArgumentUtils.getRole(event.getGuild(), argument);
			
			if (role == null && member == null && channel == null) {
				event.reply("I could not find that channel/user/role :no_entry:").queue();
				return;
			}
			
			AntiInviteUtils.insertData(event.getGuild()).run(connection, OptArgs.of("durability", "soft"));
			Get data = r.table("antiad").get(event.getGuild().getId());
			Map<String, Object> dataRan = data.run(connection);
			
			Map<String, List<String>> whitelist = (Map<String, List<String>>) dataRan.get("whitelist");
			if (channel != null) {
				String displayChannel = channel instanceof TextChannel ? ((TextChannel) channel).getAsMention() : channel.getName();
				
				List<String> channels = whitelist.get("channels");
				for (String channelId : channels) {
					if (channelId.equals(channel.getId())) {
						event.reply("That channel is already whitelisted :no_entry:").queue();
						return;
					}
				}
				
				event.reply("Invites sent in the channel " + displayChannel + " will no longer be deleted <:done:403285928233402378>").queue();
				
				channels.add(channel.getId());
				whitelist.put("channels", channels);
				data.update(r.hashMap("whitelist", whitelist)).runNoReply(connection);
			} else if (member != null) {
				List<String> users = whitelist.get("users");
				for (String userId : users) {
					if (userId.equals(member.getUser().getId())) {
						event.reply("That user is already whitelisted :no_entry:").queue();
						return;
					}
				}
				
				event.reply("Invites sent by **" + member.getUser().getAsTag() + "** will no longer be deleted <:done:403285928233402378>").queue();
				
				users.add(member.getUser().getId());
				whitelist.put("users", users);
				data.update(r.hashMap("whitelist", whitelist)).runNoReply(connection);
			} else if (role != null) {
				List<String> roles = whitelist.get("roles");
				for (String roleId : roles) {
					if (roleId.equals(role.getId())) {
						event.reply("That role is already whitelisted :no_entry:").queue();
						return;
					}
				}
				
				event.reply("Invites sent by users in the role `" + role.getName() + "` will no longer be deleted <:done:403285928233402378>").queue();
				
				roles.add(role.getId());
				whitelist.put("roles", roles);
				data.update(r.hashMap("whitelist", whitelist)).runNoReply(connection);
			}
		}
		
		@SuppressWarnings("unchecked")
		@Command(value="blacklist", aliases={"unwhitelist", "removewhitelist", "remove whitelist"}, description="Removes a whitelist from a specific user/role/channel")
		@AuthorPermissions({Permission.MANAGE_SERVER})
		public void blacklist(CommandEvent event, @Context Connection connection, @Argument(value="user | role | channel", endless=true) String argument) {
			Get data = r.table("antiad").get(event.getGuild().getId());
			Map<String, Object> dataRan = data.run(connection);
			
			if (dataRan == null) {
				event.reply("Nothing is whitelisted to send invites in this server :no_entry:").queue();
				return;
			}
			
			Member member = ArgumentUtils.getMember(event.getGuild(), argument);
			Channel channel = ArgumentUtils.getTextChannelOrParent(event.getGuild(), argument);
			Role role = ArgumentUtils.getRole(event.getGuild(), argument);
			
			if (role == null && member == null && channel == null) {
				event.reply("I could not find that channel/user/role :no_entry:").queue();
				return;
			}
			
			Map<String, List<String>> whitelist = (Map<String, List<String>>) dataRan.get("whitelist");
			if (channel != null) {
				String displayChannel = channel instanceof TextChannel ? ((TextChannel) channel).getAsMention() : channel.getName();
				
				List<String> channels = whitelist.get("channels");
				for (String channelId : channels) {
					if (channelId.equals(channel.getId())) {
						event.reply("Invites sent in the channel " + displayChannel + " will now be deleted <:done:403285928233402378>").queue();
						
						channels.remove(channel.getId());
						whitelist.put("channels", channels);
						data.update(r.hashMap("whitelist", whitelist)).runNoReply(connection);
						return;
					}
				}
				
				event.reply("That channel isn't whitelisted :no_entry:").queue();
			} else if (member != null) {
				List<String> users = whitelist.get("users");
				for (String userId : users) {
					if (userId.equals(member.getUser().getId())) {
						event.reply("Invites sent by **" + member.getUser().getAsTag() + "** will now be deleted <:done:403285928233402378>").queue();
						
						users.remove(member.getUser().getId());
						whitelist.put("users", users);
						data.update(r.hashMap("whitelist", whitelist)).runNoReply(connection);
						return;
					}
				}
				
				event.reply("That user isn't whitelisted :no_entry:").queue();
			} else if (role != null) {
				List<String> roles = whitelist.get("roles");
				for (String roleId : roles) {
					if (roleId.equals(role.getId())) {
						event.reply("Invites sent by users in the role `" + role.getName() + "` will now be deleted <:done:403285928233402378>").queue();
						
						roles.remove(role.getId());
						whitelist.put("roles", roles);
						data.update(r.hashMap("whitelist", whitelist)).runNoReply(connection);
						return;
					}
				}
				
				event.reply("That role isn't whitelisted :no_entry:").queue();
			}
		}
		
		public class WhitelistedCommand extends Sx4Command {
			
			public WhitelistedCommand() {
				super("whitelisted");
				
				super.setDescription("View everything which is whitelisted to send invites while antiinvite is active");
				super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
			}
			
			public void onCommand(CommandEvent event) {
				event.reply(HelpUtils.getHelpMessage(event.getCommand())).queue();
			}
			
			@Command(value="channels", aliases={"channel"}, description="View all the channels which are whitelisted to send invites while antiinvite is active", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
			@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
			public void channels(CommandEvent event, @Context Connection connection) {
				List<String> channelIds = r.table("antiad").get(event.getGuild().getId()).g("whitelist").g("channels").run(connection);
				
				List<Channel> channels = new ArrayList<>();
				for (String channelId : channelIds) {
					TextChannel textChannel = event.getGuild().getTextChannelById(channelId);
					if (textChannel == null) {
						Category category = event.getGuild().getCategoryById(channelId);
						if (category != null) {
							channels.add(category);
						}
					} else {
						channels.add(textChannel);
					}
				}
				
				if (channels.isEmpty()) {
					event.reply("There are no whitelisted channels :no_entry:").queue();
					return;
				}
				
				PagedResult<Channel> paged = new PagedResult<>(channels)
						.setAuthor("Whitelisted Channels", null, event.getGuild().getIconUrl())
						.setPerPage(15)
						.setDeleteMessage(false)
						.setIndexed(false)
						.setFunction(channel -> {
							return channel instanceof TextChannel ? ((TextChannel) channel).getAsMention() : channel.getName();
						});
				
				PagedUtils.getPagedResult(event, paged, 300, null);
			}
			
			@Command(value="roles", aliases={"roles"}, description="View all the roles which are whitelisted to send invites while antiinvite is active", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
			@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
			public void roles(CommandEvent event, @Context Connection connection) {
				List<String> roleIds = r.table("antiad").get(event.getGuild().getId()).g("whitelist").g("roles").run(connection);
				
				List<Role> roles = new ArrayList<>();
				for (String roleId : roleIds) {
					Role role = event.getGuild().getRoleById(roleId);
					if (role != null) {
						roles.add(role);
					}
				}
				
				if (roles.isEmpty()) {
					event.reply("There are no whitelisted roles :no_entry:").queue();
					return;
				}
				
				PagedResult<Role> paged = new PagedResult<>(roles)
						.setAuthor("Whitelisted Roles", null, event.getGuild().getIconUrl())
						.setPerPage(15)
						.setDeleteMessage(false)
						.setIndexed(false)
						.setFunction(role -> {
							return role.getAsMention();
						});
				
				PagedUtils.getPagedResult(event, paged, 300, null);
			}
			
			@Command(value="users", aliases={"user", "member", "members"}, description="View all the users which are whitelisted to send invites while antiinvite is active", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
			@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
			public void users(CommandEvent event, @Context Connection connection) {
				List<String> userIds = r.table("antiad").get(event.getGuild().getId()).g("whitelist").g("users").run(connection);
				
				List<Member> members = new ArrayList<>();
				for (String userId : userIds) {
					Member member = event.getGuild().getMemberById(userId);
					if (member != null) {
						members.add(member);
					}
				}
				
				if (members.isEmpty()) {
					event.reply("There are no whitelisted users :no_entry:").queue();
					return;
				}
				
				PagedResult<Member> paged = new PagedResult<>(members)
						.setAuthor("Whitelisted Users", null, event.getGuild().getIconUrl())
						.setPerPage(15)
						.setDeleteMessage(false)
						.setIndexed(false)
						.setFunction(member -> {
							return member.getUser().getAsTag();
						});
				
				PagedUtils.getPagedResult(event, paged, 300, null);
			}
			
		}
		
		@Command(value="stats", aliases={"setting", "settings"}, description="View all the settings for antiinvite in the current server", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
		public void stats(CommandEvent event, @Context Connection connection) {
			Map<String, Object> data = r.table("antiad").get(event.getGuild().getId()).run(connection);
			
			EmbedBuilder embed = new EmbedBuilder();
			embed.setAuthor("Anti Invite Settings", null, event.getSelfUser().getEffectiveAvatarUrl());
			embed.setColor(Settings.EMBED_COLOUR);
			embed.addField("Status", data == null ? "Disabled" : (boolean) data.get("toggle") == false ? "Disabled" : "Enabled", true);
			embed.addField("Auto Moderation", data == null ? "Disabled" : data.get("action") == null ? "Disabled" : "Sending " + (long) data.get("attempts") + " invite" + ((long) data.get("attempts") == 1 ? "" : "s") + " will result in a `" + (String) data.get("action") + "`", true);
			embed.addField("Ban Invite Names", data == null ? "Disabled" : (boolean) data.get("baninvites") == false ? "Disabled" : "Enabled", true);
			event.reply(embed.build()).queue();
		}
		
	}
	
	@Initialize(all=true)
	public void initialize(CommandImpl command) {
		command.setCategory(Categories.ANTI_INVITE);
	}
	
}
