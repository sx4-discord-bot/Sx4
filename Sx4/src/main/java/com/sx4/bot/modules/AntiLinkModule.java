package com.sx4.bot.modules;

import java.util.ArrayList;
import java.util.Collections;
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
import com.jockie.bot.core.module.Module;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Field;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Updates;
import com.sx4.bot.categories.Categories;
import com.sx4.bot.core.Sx4Command;
import com.sx4.bot.core.Sx4CommandEventListener;
import com.sx4.bot.database.Conditions;
import com.sx4.bot.database.Database;
import com.sx4.bot.interfaces.Examples;
import com.sx4.bot.settings.Settings;
import com.sx4.bot.utils.ArgumentUtils;
import com.sx4.bot.utils.GeneralUtils;
import com.sx4.bot.utils.HelpUtils;
import com.sx4.bot.utils.PagedUtils;
import com.sx4.bot.utils.PagedUtils.PagedResult;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Category;
import net.dv8tion.jda.api.entities.GuildChannel;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.TextChannel;

@Module
public class AntiLinkModule {

	public class AntiLinkCommand extends Sx4Command {
		
		private final List<String> actions = List.of("mute", "kick", "ban");
		private final List<String> nullStrings = List.of("null", "none", "off", "reset");
		
		public AntiLinkCommand() {
			super("antilink");
			
			super.setAliases("anti link", "anti-link");
			super.setDescription("Set up antilink to automatically delete any active discord links which are sent in the server");
			super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
			super.setExamples("antilink toggle", "antilink action", "antilink stats");
		}
		
		public void onCommand(CommandEvent event) {
			event.reply(HelpUtils.getHelpMessage(event.getCommand())).queue();
		}
		
		@Command(value="toggle", aliases={"enable", "disable"}, description="Enable/disable anti-link for the current server", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@Examples({"antilink toggle"})
		@AuthorPermissions({Permission.MESSAGE_MANAGE})
		public void toggle(CommandEvent event, @Context Database database) {
			List<Bson> update = List.of(Aggregates.addFields(new Field<>("antilink.enabled", Conditions.cond("$antilink.enabled", "$$REMOVE", true))));
			database.getGuildByIdAndUpdate(event.getGuild().getIdLong(), update, Projections.include("antilink.enabled"), (data, exception) -> {
				if (exception != null) {
					exception.printStackTrace();
					event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
				} else {
					event.reply("Anti-Link is now " + (data.getEmbedded(List.of("antilink", "enabled"), false) ? "enabled" : "disabled") + " in this server <:done:403285928233402378>").queue();
				}
			});
		}
		
		@Command(value="action", aliases={"set action", "setaction"}, description="Set the action which will happen when a user posts a certain amount of links (Set with antilink attempts), use off as an argument to turn this feature off", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@Examples({"antilink action ban", "antilink action mute", "antilink action none"})
		@AuthorPermissions({Permission.BAN_MEMBERS})
		public void action(CommandEvent event, @Context Database database, @Argument(value="action") String actionArgument) {
			String action = actionArgument.toLowerCase();
			
			Document data = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("antilink.action", "antilink.attempts")).get("antilink", Database.EMPTY_DOCUMENT);
			if (nullStrings.contains(action)) {
				if (data.getString("action") == null) {
					event.reply("You don't have an action set :no_entry:").queue();
					return;
				}
				
				int attempts = data.getInteger("attempts", 3);
				database.updateGuildById(event.getGuild().getIdLong(), Updates.unset("antilink.action"), (result, exception) -> {
					if (exception != null) {
						exception.printStackTrace();
						event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
					} else {
						event.reply("An action will no longer occur when a user sends " + attempts + " link" + (attempts == 1 ? "" : "s") + " <:done:403285928233402378>").queue();
					}
				});
			} else if (actions.contains(action)) {
				if (data.getString("action") != null && data.getString("action").equals(action)) {
					event.reply("The action is already set to `" + action + "` :no_entry:").queue();
					return;
				}
				
				int attempts = data.getInteger("attempts", 3);
				database.updateGuildById(event.getGuild().getIdLong(), Updates.set("antilink.action", action), (result, exception) -> {
					if (exception != null) {
						exception.printStackTrace();
						event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
					} else {
						event.reply("Users will now receive a `" + action + "` when sending **" + attempts + "** link" + (attempts == 1 ? "" : "s") + " <:done:403285928233402378>").queue();
					}
				});
			} else {
				event.reply("Invalid action, `" + GeneralUtils.joinGrammatical(actions) + "` are the valid actions :no_entry:").queue();
			}
		}
		
		@Command(value="attempts", description="Set the amount of times a user can send an link before an action occurs to them set through `antilink action`", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@Examples({"antilink attempts 5", "antilink attempts 2"})
		@AuthorPermissions({Permission.MESSAGE_MANAGE})
		public void attempts(CommandEvent event, @Context Database database, @Argument(value="attempts") int attempts) {
			if (attempts < 1) {
				event.reply("The attempts cannot be any lower than 1 :no_entry:").queue();
				return;
			}
			
			int dataAttempts = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("antilink.attempts")).getEmbedded(List.of("antilink", "attempts"), 3);
			if (dataAttempts == attempts) {
				event.reply("Attempts is already set to **" + attempts + "** :no_entry:").queue();
				return;
			}
			
			database.updateGuildById(event.getGuild().getIdLong(), Updates.set("antilink.attempts", attempts), (result, exception) -> {
				if (exception != null) {
					exception.printStackTrace();
					event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
				} else {
					event.reply("When a user sends **" + attempts + "** link" + (attempts == 1 ? "" : "s") + " an action will occur to them <:done:403285928233402378>").queue();
				}
			});
		}
		
		@Command(value="reset attempts", aliases={"resetattempts"}, description="Resets a users attempts of sending links to 0", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@Examples({"antilink reset attempts @Shea#6653", "antilink reset attempts 402557516728369153", "antilink reset attempts Shea"})
		@AuthorPermissions({Permission.MESSAGE_MANAGE})
		public void resetAttempts(CommandEvent event, @Context Database database, @Argument(value="user", endless=true) String userArgument) {
			Member member = ArgumentUtils.getMember(event.getGuild(), userArgument);
			if (member == null) {
				event.reply("I could not find that user :no_entry:").queue();
				return;
			}
			
			List<Document> users = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("antilink.users")).getEmbedded(List.of("antilink", "users"), Collections.emptyList());
			for (Document userData : users) {
				if (userData.getLong("id") == member.getIdLong()) {
					database.updateGuildById(event.getGuild().getIdLong(), Updates.pull("antilink.users", Filters.eq("id", member.getIdLong())), (result, exception) -> {
						if (exception != null) {
							exception.printStackTrace();
							event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
						} else {
							event.reply("Attempts for **" + member.getUser().getAsTag() + "** have been reset <:done:403285928233402378>").queue();
						}
					});
					
					return;
				}
			}
			
			event.reply("**" + member.getUser().getAsTag() + "** does not have any attempts :no_entry:").queue();
		}
		
		@Command(value="whitelist", description="Whitelists a user/channel/role to be able to send links")
		@Examples({"antilink whitelist @Shea#6653", "antilink whitelist #general", "antilink whitelist @everyone"})
		@AuthorPermissions({Permission.MANAGE_SERVER})
		public void whitelist(CommandEvent event, @Context Database database, @Argument(value="user | role | channel", endless=true) String argument) {
			Member member = ArgumentUtils.getMember(event.getGuild(), argument);
			GuildChannel channel = ArgumentUtils.getGuildChannel(event.getGuild(), argument);
			Role role = ArgumentUtils.getRole(event.getGuild(), argument);
			
			if (role == null && member == null && channel == null) {
				event.reply("I could not find that channel/user/role :no_entry:").queue();
				return;
			}
			
			Document whitelist = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("antilink.whitelist")).getEmbedded(List.of("antilink", "whitelist"), Database.EMPTY_DOCUMENT);
			if (channel != null) {
				String displayChannel = channel instanceof TextChannel ? ((TextChannel) channel).getAsMention() : channel.getName();
				
				List<Long> channels = whitelist.getList("channels", Long.class, Collections.emptyList());
				for (long channelId : channels) {
					if (channelId == channel.getIdLong()) {
						event.reply("That channel is already whitelisted :no_entry:").queue();
						return;
					}
				}
				
				database.updateGuildById(event.getGuild().getIdLong(), Updates.push("antilink.whitelist.channels", channel.getIdLong()), (result, exception) -> {
					if (exception != null) {
						exception.printStackTrace();
						event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
					} else {
						event.reply("Links sent in the channel " + displayChannel + " will no longer be deleted <:done:403285928233402378>").queue();
					}
				});
			} else if (member != null) {
				List<Long> users = whitelist.getList("users", Long.class, Collections.emptyList());
				for (long userId : users) {
					if (userId == member.getIdLong()) {
						event.reply("That user is already whitelisted :no_entry:").queue();
						return;
					}
				}
				
				database.updateGuildById(event.getGuild().getIdLong(), Updates.push("antilink.whitelist.users", member.getIdLong()), (result, exception) -> {
					if (exception != null) {
						exception.printStackTrace();
						event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
					} else {
						event.reply("Links sent by **" + member.getUser().getAsTag() + "** will no longer be deleted <:done:403285928233402378>").queue();
					}
				});
			} else if (role != null) {
				List<Long> roles = whitelist.getList("roles", Long.class, Collections.emptyList());
				for (long roleId : roles) {
					if (roleId == role.getIdLong()) {
						event.reply("That role is already whitelisted :no_entry:").queue();
						return;
					}
				}
				
				database.updateGuildById(event.getGuild().getIdLong(), Updates.push("antilink.whitelist.roles", role.getIdLong()), (result, exception) -> {
					if (exception != null) {
						exception.printStackTrace();
						event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
					} else {
						event.reply("Links sent by users in the role `" + role.getName() + "` will no longer be deleted <:done:403285928233402378>").queue();
					}
				});
			}
		}
		
		@Command(value="blacklist", aliases={"unwhitelist", "removewhitelist", "remove whitelist"}, description="Removes a whitelist from a specific user/role/channel")
		@Examples({"antilink blacklist @Shea#6653", "antilink blacklist #general", "antilink blacklist @everyone"})
		@AuthorPermissions({Permission.MANAGE_SERVER})
		public void blacklist(CommandEvent event, @Context Database database, @Argument(value="user | role | channel", endless=true) String argument) {
			Document whitelist = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("antilink.whitelist")).getEmbedded(List.of("antilink", "whitelist"), Database.EMPTY_DOCUMENT);
			if (whitelist.isEmpty()) {
				event.reply("Nothing is whitelisted to send links in this server :no_entry:").queue();
				return;
			}
			
			Member member = ArgumentUtils.getMember(event.getGuild(), argument);
			GuildChannel channel = ArgumentUtils.getGuildChannel(event.getGuild(), argument);
			Role role = ArgumentUtils.getRole(event.getGuild(), argument);
			
			if (role == null && member == null && channel == null) {
				event.reply("I could not find that channel/user/role :no_entry:").queue();
				return;
			}
			
			if (channel != null) {
				String displayChannel = channel instanceof TextChannel ? ((TextChannel) channel).getAsMention() : channel.getName();
				
				List<Long> channels = whitelist.getList("channels", Long.class, Collections.emptyList());
				for (long channelId : channels) {
					if (channelId == channel.getIdLong()) {
						database.updateGuildById(event.getGuild().getIdLong(), Updates.pull("antilink.whitelist.channels", channel.getIdLong()), (result, exception) -> {
							if (exception != null) {
								exception.printStackTrace();
								event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
							} else {
								event.reply("Links sent in the channel " + displayChannel + " will now be deleted <:done:403285928233402378>").queue();
							}
						});
						
						return;
					}
				}
				
				event.reply("That channel isn't whitelisted :no_entry:").queue();
			} else if (member != null) {
				List<Long> users = whitelist.getList("users", Long.class, Collections.emptyList());
				for (long userId : users) {
					if (userId == member.getIdLong()) {
						database.updateGuildById(event.getGuild().getIdLong(), Updates.pull("antilink.whitelist.users", member.getIdLong()), (result, exception) -> {
							if (exception != null) {
								exception.printStackTrace();
								event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
							} else {
								event.reply("Links sent by **" + member.getUser().getAsTag() + "** will now be deleted <:done:403285928233402378>").queue();
							}
						});
						
						return;
					}
				}
				
				event.reply("That user isn't whitelisted :no_entry:").queue();
			} else if (role != null) {
				List<Long> roles = whitelist.getList("roles", Long.class, Collections.emptyList());
				for (long roleId : roles) {
					if (roleId == role.getIdLong()) {
						database.updateGuildById(event.getGuild().getIdLong(), Updates.pull("antilink.whitelist.roles", role.getIdLong()), (result, exception) -> {
							if (exception != null) {
								exception.printStackTrace();
								event.reply(Sx4CommandEventListener.getUserErrorMessage(exception)).queue();
							} else {
								event.reply("Links sent by users in the role `" + role.getName() + "` will now be deleted <:done:403285928233402378>").queue();
							}
						});
						
						return;
					}
				}
				
				event.reply("That role isn't whitelisted :no_entry:").queue();
			}
		}
		
		public class WhitelistedCommand extends Sx4Command {
			
			public WhitelistedCommand() {
				super("whitelisted");
				
				super.setDescription("View everything which is whitelisted to send links while antilink is active");
				super.setBotDiscordPermissions(Permission.MESSAGE_EMBED_LINKS);
				super.setExamples("antilink whitelisted channels", "antilink whitelisted roles", "antilink whitelisted users");
			}
			
			public void onCommand(CommandEvent event) {
				event.reply(HelpUtils.getHelpMessage(event.getCommand())).queue();
			}
			
			@Command(value="channels", aliases={"channel"}, description="View all the channels which are whitelisted to send links while antilink is active", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
			@Examples({"antilink whitelisted channels"})
			@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
			public void channels(CommandEvent event, @Context Database database) {
				List<Long> channelIds = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("antilink.whitelist.channels")).getEmbedded(List.of("antilink", "whitelist", "channels"), Collections.emptyList());
				
				List<GuildChannel> channels = new ArrayList<>();
				for (long channelId : channelIds) {
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
				
				PagedResult<GuildChannel> paged = new PagedResult<>(channels)
						.setAuthor("Whitelisted Channels", null, event.getGuild().getIconUrl())
						.setPerPage(15)
						.setDeleteMessage(false)
						.setIndexed(false)
						.setFunction(channel -> {
							return channel instanceof TextChannel ? ((TextChannel) channel).getAsMention() : channel.getName();
						});
				
				PagedUtils.getPagedResult(event, paged, 300, null);
			}
			
			@Command(value="roles", aliases={"roles"}, description="View all the roles which are whitelisted to send links while antilink is active", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
			@Examples({"antilink whitelisted roles"})
			@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
			public void roles(CommandEvent event, @Context Database database) {
				List<Long> roleIds = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("antilink.whitelist.roles")).getEmbedded(List.of("antilink", "whitelist", "roles"), Collections.emptyList());
				
				List<Role> roles = new ArrayList<>();
				for (long roleId : roleIds) {
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
			
			@Command(value="users", aliases={"user", "member", "members"}, description="View all the users which are whitelisted to send links while antilink is active", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
			@Examples({"antilink whitelisted users"})
			@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
			public void users(CommandEvent event, @Context Database database) {
				List<Long> userIds = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("antilink.whitelist.users")).getEmbedded(List.of("antilink", "whitelist", "users"), Collections.emptyList());
				
				List<Member> members = new ArrayList<>();
				for (long userId : userIds) {
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
		
		@Command(value="stats", aliases={"setting", "settings"}, description="View all the settings for antilink in the current server", contentOverflowPolicy=ContentOverflowPolicy.IGNORE)
		@Examples({"antilink stats"})
		@BotPermissions({Permission.MESSAGE_EMBED_LINKS})
		public void stats(CommandEvent event, @Context Database database) {
			Bson projection = Projections.include("antilink.enabled", "antilink.action", "antilink.attempts");
			Document data = database.getGuildById(event.getGuild().getIdLong(), null, projection).get("antilink", Database.EMPTY_DOCUMENT);
			
			EmbedBuilder embed = new EmbedBuilder();
			embed.setAuthor("Anti Link Settings", null, event.getSelfUser().getEffectiveAvatarUrl());
			embed.setColor(Settings.EMBED_COLOUR);
			embed.addField("Status", data.getBoolean("enabled", false) ? "Enabled" : "Disabled", true);
			embed.addField("Auto Moderation", data.getString("action") == null ? "Disabled" : "Sending " + data.getInteger("attempts", 3) + " link" + (data.getInteger("attempts", 3) == 1 ? "" : "s") + " will result in a `" + data.getString("action") + "`", true);
			event.reply(embed.build()).queue();
		}
		
	}
	
	@Initialize(all=true, subCommands=true, recursive=true)
	public void initialize(CommandImpl command) {
		command.setCategory(Categories.ANTI_LINK);
	}
	
}
