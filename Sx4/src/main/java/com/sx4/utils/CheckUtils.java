package com.sx4.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

import org.bson.Document;

import com.jockie.bot.core.command.impl.CommandEvent;
import com.mongodb.client.model.Projections;
import com.sx4.database.Database;
import com.sx4.settings.Settings;

import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.PermissionOverride;
import net.dv8tion.jda.api.entities.Role;

public class CheckUtils {
	
	public static boolean canReply(Message message, String prefix) {
		if (!Settings.CANARY) {
			String botId = message.getJDA().getSelfUser().getId();
			boolean mentionPrefix = prefix.equals("<@" + botId + ">") || prefix.equals("<@!" + botId + ">");
			
			List<String> canaryPrefixes = ModUtils.getPrefixes(message.getGuild(), message.getAuthor(), false);
			if (canaryPrefixes.contains(prefix)) {
				Member canaryBot = message.getGuild().getMemberById(Settings.CANARY_BOT_ID);
				if (canaryBot != null && !mentionPrefix && message.getTextChannel().canTalk(canaryBot) && !canaryBot.getOnlineStatus().equals(OnlineStatus.OFFLINE)) {
					return false;
				}
			}
		}
		
		return true;
	}

	public static boolean checkPermissions(CommandEvent event, EnumSet<Permission> permissions, boolean reply) {
		Database database = Database.get();
		if (event.isAuthorDeveloper()) {
			return true;
		} else {
			long rolePerms = 0, userPerms = 0;
			Document data = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("fakePermissions.users", "fakePermissions.roles")).get("fakePermissions", Database.EMPTY_DOCUMENT);
			
			List<Document> users = data.getList("users", Document.class, Collections.emptyList());
			for (Document userData : users) {
				if (userData.getLong("id") == event.getAuthor().getIdLong()) {
					userPerms = userData.getLong("permissions");
				}
			}
			
			List<Document> roles = data.getList("roles", Document.class, Collections.emptyList());
			for (Role role : event.getMember().getRoles()) {
				for (Document roleData : roles) {
					if (roleData.getLong("id") == role.getIdLong()) {
						rolePerms |= roleData.getLong("permissions");
						
						PermissionOverride roleOverrides = event.getTextChannel().getPermissionOverride(role);
						if (roleOverrides != null) {
							rolePerms |= roleOverrides.getAllowedRaw();
						}
					}
				}
			}
			
			PermissionOverride userOverrides = event.getTextChannel().getPermissionOverride(event.getMember());
			long totalPerms = rolePerms | userPerms | Permission.getRaw(event.getMember().getPermissions()) | (userOverrides == null ? 0 : userOverrides.getAllowedRaw());
			
			EnumSet<Permission> userPermissions = Permission.getPermissions(totalPerms);
			List<Permission> missingPermissions = new ArrayList<>();
			if (userPermissions.contains(Permission.ADMINISTRATOR)) {
				return true;
			} else {
				for (Permission permission : permissions) {
					if (!userPermissions.contains(permission)) {
						missingPermissions.add(permission);
					}
				}
				
				if (missingPermissions.isEmpty()) {
					return true;
				} else {
					if (reply) {
						StringBuilder stringPermissions = new StringBuilder();
						if (missingPermissions.size() == 1) {
							stringPermissions.append(missingPermissions.get(0).getName());
						} else {
							for (int i = 0; i < missingPermissions.size(); i++) {
								if (i != missingPermissions.size() - 1) {
									stringPermissions.append(missingPermissions.get(i).getName() + (i != missingPermissions.size() - 2 ? ", " : " "));
								} else {
									stringPermissions.append("and " + missingPermissions.get(i).getName());
								}
							}
						}
						
						event.reply("You are missing the permission" + (missingPermissions.size() == 1 ? " " : "s ") + stringPermissions.toString() + " to execute this command :no_entry:").queue();
					}
					
					return false;
				}
			}
		} 
	}
	
	public static boolean checkBlacklist(CommandEvent event) {
		if (!event.isAuthorDeveloper()) {
			Database database = Database.get();
			
			boolean userBlacklisted = database.getUserById(event.getAuthor().getIdLong(), null, Projections.include("blacklisted")).getBoolean("blacklisted", false);
			if (userBlacklisted && !event.getCommand().getCommand().equals("support")) {
				event.reply("You are blacklisted from using the bot, to appeal make sure to join the bots support server which can be found in `" + event.getPrefix() + "support`").queue();
				return false;
			} else {
				if (!CheckUtils.checkPermissions(event, EnumSet.of(Permission.ADMINISTRATOR), false)) {
					Document data = database.getGuildById(event.getGuild().getIdLong(), null, Projections.include("blacklist.commands", "blacklist.disabled")).get("blacklist", Database.EMPTY_DOCUMENT);
					
					List<Document> commands = data.getList("commands", Document.class, Collections.emptyList());					
					for (Document command : commands) {
						if (event.getCommand().getTopParent().getCategory().getName().equals(command.getString("id")) || event.getCommand().getCommandTrigger().equals(command.getString("id")) || event.getCommand().getTopParent().getCommandTrigger().equals(command.getString("id"))) {
							Document whitelisted = command.get("whitelisted", Database.EMPTY_DOCUMENT);
							
							List<Long> whitelistedChannels = whitelisted.getList("channels", Long.class, Collections.emptyList());
							for (long channelId : whitelistedChannels) {
								if (channelId == event.getChannel().getIdLong() || channelId == event.getTextChannel().getParent().getIdLong()) {
									return true;
								}
							}
							
							List<Long> whitelistedUsers = whitelisted.getList("users", Long.class, Collections.emptyList());
							for (long userId : whitelistedUsers) {
								if (userId == event.getAuthor().getIdLong()) {
									return true;
								}
							}
							
							List<Long> whitelistedRoles = whitelisted.getList("roles", Long.class, Collections.emptyList());
							for (long roleId : whitelistedRoles) {
								for (Role role : event.getMember().getRoles()) {
									if (roleId == role.getIdLong()) {
										return true;
									}
								}
							}
						}
					}
					
					for (Document command : commands) {
						if (event.getCommand().getTopParent().getCategory().getName().equals(command.getString("id")) || event.getCommand().getCommandTrigger().equals(command.getString("id")) || event.getCommand().getTopParent().getCommandTrigger().equals(command.getString("id"))) {
							Document blacklisted = command.get("blacklisted", Database.EMPTY_DOCUMENT);
							
							List<Long> blacklistedChannels = blacklisted.getList("channels", Long.class, Collections.emptyList());
							for (long channelId : blacklistedChannels) {
								if (channelId == event.getChannel().getIdLong() || channelId == event.getTextChannel().getParent().getIdLong()) {
									event.reply("You cannot use this command in this channel :no_entry:").queue();
									return false;
								}
							}
							
							List<Long> blacklistedUsers = blacklisted.getList("users", Long.class, Collections.emptyList());
							for (long userId : blacklistedUsers) {
								if (userId == event.getAuthor().getIdLong()) {
									event.reply("You have been blacklisted from using this command in this server :no_entry:").queue();
									return false;
								}
							}
							
							List<Long> blacklistedRoles = blacklisted.getList("roles", Long.class, Collections.emptyList());
							for (long roleId : blacklistedRoles) {
								for (Role role : event.getMember().getRoles()) {
									if (roleId == role.getIdLong()) {
										event.reply("You are in the role `" + role.getName() + "` which means you cannot use this command :no_entry:").queue();
										return false;
									}
								}
							}
						}
					}
					
					List<String> disabledCommands = data.getList("disabled", String.class, Collections.emptyList());
					if (disabledCommands.contains(event.getCommand().getCommandTrigger()) || disabledCommands.contains(event.getCommand().getTopParent().getCommandTrigger()) || disabledCommands.contains(event.getCommand().getTopParent().getCategory().getName())) {
						event.reply("That command is disabled in this server :no_entry:").queue();
						return false;
					}
				}
			}
		}
		
		return true;
	}

}
