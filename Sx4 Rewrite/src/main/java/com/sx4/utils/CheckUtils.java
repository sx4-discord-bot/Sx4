package com.sx4.utils;

import static com.rethinkdb.RethinkDB.r;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.jockie.bot.core.command.impl.CommandEvent;
import com.rethinkdb.gen.ast.Get;
import com.rethinkdb.net.Connection;

import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.PermissionOverride;
import net.dv8tion.jda.core.entities.Role;

public class CheckUtils {

	@SuppressWarnings("unchecked")
	public static boolean checkPermissions(CommandEvent event, Connection connection, Permission[] permissions, boolean reply) {
		Get data = r.table("fakeperms").get(event.getGuild().getId());
		Map<String, Object> dataRan = data.run(connection);
		long rolePerms = 0;
		long userPerms = 0;
		if (event.getCommandListener().isDeveloper(event.getAuthor().getIdLong())) {
			return true;
		} else if (event.getGuild().getOwner().equals(event.getMember())) {
			return true;
		} else if (dataRan != null) {
			List<Map<String, Object>> users = (List<Map<String, Object>>) dataRan.get("users");
			
			Map<String, Object> user = null;
			for (Map<String, Object> userData : users) {
				if (userData.get("id").equals(event.getAuthor().getId())) {
					user = userData;
				}
			}
			
			List<Map<String, Object>> roles = (List<Map<String, Object>>) dataRan.get("roles");
			if (user != null) {
				userPerms = (long) user.get("perms");
			}
			
			for (Role role : event.getMember().getRoles()) {
				for (Map<String, Object> roleData : roles) {
					if (roleData.get("id").equals(role.getId())) {
						rolePerms |= (long) roleData.get("perms");
						if (event.getTextChannel().getPermissionOverride(role) != null) {
							rolePerms |= event.getTextChannel().getPermissionOverride(role).getAllowedRaw();
						}
					}
				}
			}
			
			PermissionOverride userOverrides = event.getTextChannel().getPermissionOverride(event.getMember());
			long totalPerms = rolePerms | userPerms | Permission.getRaw(event.getMember().getPermissions()) | (userOverrides == null ? 0 : userOverrides.getAllowedRaw());
			
			List<Permission> userPermissions = Permission.getPermissions(totalPerms);
			List<Permission> missingPermissions = new ArrayList<Permission>();
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
					if (reply == true) {
						String stringPermissions = "";
						if (missingPermissions.size() == 1) {
							stringPermissions = missingPermissions.get(0).getName();
						} else {
							for (int i = 0; i < missingPermissions.size(); i++) {
								if (i != missingPermissions.size()) {
									stringPermissions += missingPermissions.get(i).getName() + (i != missingPermissions.size() - 1 ? ", " : " ");
								} else {
									stringPermissions += "and " + missingPermissions.get(i).getName();
								}
							}
						}
						
						event.reply("You are missing the permission" + (missingPermissions.size() == 1 ? " " : "s ") + stringPermissions + " to execute this command :no_entry:").queue();
					}
					
					return false;
				}
			}
		} else {
			PermissionOverride userOverrides = event.getTextChannel().getPermissionOverride(event.getMember());

			for (Role role : event.getMember().getRoles()) {
				if (event.getTextChannel().getPermissionOverride(role) != null) {
					rolePerms |= event.getTextChannel().getPermissionOverride(role).getAllowedRaw();
				}
			}
			List<Permission> userPermissions = Permission.getPermissions(Permission.getRaw(event.getMember().getPermissions()) | (userOverrides == null ? 0 : userOverrides.getAllowedRaw()) | rolePerms);
			List<Permission> missingPermissions = new ArrayList<Permission>();
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
					if (reply == true) {
						String stringPermissions = "";
						if (missingPermissions.size() == 1) {
							stringPermissions = missingPermissions.get(0).getName();
						} else {
							for (int i = 0; i < missingPermissions.size(); i++) {
								if (i != missingPermissions.size()) {
									stringPermissions += missingPermissions.get(i).getName() + (i != missingPermissions.size() - 1 ? ", " : " ");
								} else {
									stringPermissions += "and " + missingPermissions.get(i).getName();
								}
							}
						}
						event.reply("You are missing the permission" + (missingPermissions.size() == 1 ? " " : "s ") + stringPermissions + " to execute this command :no_entry:").queue();
					}
					
					return false;
				}
			}
		}
	}
	
	@SuppressWarnings("unchecked")
	public static boolean checkBlacklist(CommandEvent event, Connection connection) {
		if (!event.getCommandListener().isDeveloper(event.getMember().getUser().getIdLong())) {
			List<String> botBlacklistData = r.table("blacklist").get("owner").g("users").run(connection);			
			if (botBlacklistData.contains(event.getMember().getUser().getId()) && !event.getCommand().getCommand().equals("support")) {
				event.reply("You are blacklisted from using the bot, to appeal make sure to join the bots support server which can be found in `" + event.getPrefix() + "support`").queue();
				return false;
			} else {
				Permission[] administrator = {Permission.ADMINISTRATOR};
				if (checkPermissions(event, connection, administrator, false) == false) {
					Map<String, Object> blacklistData = r.table("blacklist").get(event.getGuild().getId()).run(connection);
					if (blacklistData != null) {
						List<String> disabledCommands = (List<String>) blacklistData.get("disabled");
						List<Map<String, Object>> commands = (List<Map<String, Object>>) blacklistData.get("commands");					
						for (Map<String, Object> command : commands) {
							if (event.getCommand().getTopParent().getCategory().getName().equals(command.get("id")) || event.getCommand().getCommandTrigger().equals(command.get("id")) || event.getCommand().getTopParent().getCommandTrigger().equals(command.get("id"))) {
								List<Map<String, String>> whitelisted = (List<Map<String, String>>) command.get("whitelisted");	
								List<Map<String, String>> blacklisted = (List<Map<String, String>>) command.get("blacklisted");	
								for (Map<String, String> whitelist : whitelisted) {
									if (whitelist.get("type").equals("channel")) {
										if (whitelist.get("id").equals(event.getChannel().getId()) || whitelist.get("id").equals(event.getTextChannel().getParent().getId())) {
											return true;
										}
									} else if (whitelist.get("type").equals("user")) {
										if (whitelist.get("id").equals(event.getMember().getUser().getId())) {
											return true;
										}
									} else if (whitelist.get("type").equals("role")) {
										for (Role role : event.getMember().getRoles()) {
											if (role.getId().equals(whitelist.get("id"))) {
												return true;
											}
										}
									}
								}
								
								for (Map<String, String> blacklist : blacklisted) {
									if (blacklist.get("type").equals("channel")) {
										if (blacklist.get("id").equals(event.getChannel().getId()) || blacklist.get("id").equals(event.getTextChannel().getParent().getId())) {
											event.reply("You cannot use this command in this channel :no_entry:").queue();
											return false;
										}
									} else if (blacklist.get("type").equals("user")) {
										if (blacklist.get("id").equals(event.getMember().getUser().getId())) {
											event.reply("You have been blacklisted from using this command in this server :no_entry:").queue();
											return false;
										}
									} else if (blacklist.get("type").equals("role")) {
										for (Role role : event.getMember().getRoles()) {
											if (role.getId().equals(blacklist.get("id"))) {
												event.reply("You are in the role `" + role.getName() + "` which means you cannot use this command :no_entry:").queue();
												return false;
											}
										}
									}
								}
							
								if (disabledCommands.contains(command.get("id"))) {
									event.reply("This command is disabled in this server :no_entry:").queue();
									return false;
								}
							}
						}
					}
				}
			}
		}
		
		return true;
	}

}
