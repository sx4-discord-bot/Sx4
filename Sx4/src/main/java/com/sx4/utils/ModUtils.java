package com.sx4.utils;


import static com.rethinkdb.RethinkDB.r;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.rethinkdb.gen.ast.Get;
import com.rethinkdb.model.OptArgs;
import com.rethinkdb.net.Connection;
import com.sx4.core.Sx4Bot;
import com.sx4.events.MuteEvents;
import com.sx4.settings.Settings;

import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Member;
import net.dv8tion.jda.core.entities.MessageEmbed;
import net.dv8tion.jda.core.entities.PermissionOverride;
import net.dv8tion.jda.core.entities.Role;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.entities.User;
import net.dv8tion.jda.core.entities.VoiceChannel;
import net.dv8tion.jda.core.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.core.utils.PermissionUtil;

public class ModUtils {
	
	public static final List<Map<String, Object>> DEFAULT_WARN_CONFIG = new ArrayList<>();
	
	static {
		Map<String, Object> secondWarning = new HashMap<>();
		secondWarning.put("action", "mute");
		secondWarning.put("warning", 2);
		secondWarning.put("time", 1800);
		DEFAULT_WARN_CONFIG.add(secondWarning);
		Map<String, Object> thirdWarning = new HashMap<>();
		thirdWarning.put("action", "kick");
		thirdWarning.put("warning", 3);
		DEFAULT_WARN_CONFIG.add(thirdWarning);
		Map<String, Object> fourthWarning = new HashMap<>();
		fourthWarning.put("action", "ban");
		fourthWarning.put("warning", 4);
		DEFAULT_WARN_CONFIG.add(fourthWarning);
	}
	
	public static void setupMuteRole(Guild guild, Consumer<Role> muteRole, Consumer<String> error) {
		String roleName = "Muted - " + guild.getSelfMember().getUser().getName();
		
		Role role = MuteEvents.getMuteRole(guild);
		if (role != null) {
			muteRole.accept(role);

			for (TextChannel channel : guild.getTextChannels()) {
				PermissionOverride roleOverrides = channel.getPermissionOverride(role);
				List<Permission> deniedPermissions = roleOverrides == null ? new ArrayList<>() : new ArrayList<>(roleOverrides.getDenied());
				if (!deniedPermissions.contains(Permission.MESSAGE_WRITE)) {
					deniedPermissions.add(Permission.MESSAGE_WRITE);
					try {
						channel.putPermissionOverride(role).setPermissions(roleOverrides == null ? null : roleOverrides.getAllowed(), deniedPermissions).queue();
					} catch(InsufficientPermissionException e) {
						continue;
					}
				}
			}

		} else {
			for (Role guildRole : guild.getRoles()) {
				if (guildRole.getName().equals(roleName)) {
					muteRole.accept(guildRole);
					MuteEvents.putMuteRole(guild.getId(), guildRole.getId());
					
					for (TextChannel channel : guild.getTextChannels()) {
						PermissionOverride roleOverrides = channel.getPermissionOverride(guildRole);
						List<Permission> deniedPermissions = roleOverrides == null ? new ArrayList<>() : new ArrayList<>(roleOverrides.getDenied());
						if (!deniedPermissions.contains(Permission.MESSAGE_WRITE)) {
							deniedPermissions.add(Permission.MESSAGE_WRITE);
							try {
								channel.putPermissionOverride(guildRole).setPermissions(roleOverrides == null ? null : roleOverrides.getAllowed(), deniedPermissions).queue();
							} catch(InsufficientPermissionException e) {
								continue;
							}
						}
					}
					
					return;
				}
			}
			
			if (guild.getRoles().size() >= 250) {
				error.accept("I cannot create the mute role because the server has the max amount of roles (250) :no_entry:");		
				muteRole.accept(null);
				return;
			}
			
			guild.getController().createRole().setName(roleName).queue(newRole -> {
				muteRole.accept(newRole);
				MuteEvents.putMuteRole(guild.getId(), newRole.getId());
				
				for (TextChannel channel : guild.getTextChannels()) {
					PermissionOverride roleOverrides = channel.getPermissionOverride(newRole);
					List<Permission> deniedPermissions = roleOverrides == null ? new ArrayList<>() : new ArrayList<>(roleOverrides.getDenied());
					if (!deniedPermissions.contains(Permission.MESSAGE_WRITE)) {
						deniedPermissions.add(Permission.MESSAGE_WRITE);
						try {
							channel.putPermissionOverride(newRole).setPermissions(roleOverrides == null ? null : roleOverrides.getAllowed(), deniedPermissions).queue();
						} catch(InsufficientPermissionException e) {
							continue;
						}
					}
				}
				
				return;
			});
		}
	}
	
	public static boolean canConnect(Member member, VoiceChannel voiceChannel) {
	    EnumSet<Permission> perms = Permission.toEnumSet(PermissionUtil.getEffectivePermission(voiceChannel, member));
	    if (!perms.contains(Permission.VOICE_CONNECT)) {
	        return false;
	    }
	    
	    final int userLimit = voiceChannel.getUserLimit();
	    if (userLimit > 0 && !perms.contains(Permission.ADMINISTRATOR)) {
	        if (userLimit <= voiceChannel.getMembers().size() && !perms.contains(Permission.VOICE_MOVE_OTHERS)) {
	            return false;
	        }
	    }
	    
	    return true;
	}
	
	public static MessageEmbed getKickEmbed(Guild guild, User moderator, String reason) {
		EmbedBuilder embed = new EmbedBuilder();
		embed.setAuthor("You have been kicked from " + guild.getName(), null, guild.getIconUrl());
		embed.setTimestamp(Instant.now());
		embed.addField("Moderator", moderator.getAsTag() + " (" + moderator.getId() + ")", false);
		embed.addField("Reason", reason == null ? "None Given" : reason, false);
		return embed.build();
	}
	
	public static MessageEmbed getBanEmbed(Guild guild, User moderator, String reason) {
		EmbedBuilder embed = new EmbedBuilder();
		embed.setAuthor("You have been banned from " + guild.getName(), null, guild.getIconUrl());
		embed.setTimestamp(Instant.now());
		embed.addField("Moderator", moderator.getAsTag() + " (" + moderator.getId() + ")", false);
		embed.addField("Reason", reason == null ? "None Given" : reason, false);
		return embed.build();
	}
	
	public static MessageEmbed getMuteEmbed(Guild guild, TextChannel channel, User moderator, long length, String reason) {
		EmbedBuilder embed = new EmbedBuilder();
		embed.setAuthor("You have been muted in " + guild.getName(), null, guild.getIconUrl());
		embed.setTimestamp(Instant.now());
		embed.addField("Channel", channel == null ? "All" : channel.getAsMention(), false);
		embed.addField("Length", length == 0 ? "Infinite" : TimeUtils.toTimeString(length, ChronoUnit.SECONDS), false);
		embed.addField("Moderator", moderator.getAsTag() + " (" + moderator.getId() + ")", false);
		embed.addField("Reason", reason == null ? "None Given" : reason, false);
		return embed.build();
	}
	
	public static MessageEmbed getUnmuteEmbed(Guild guild, TextChannel channel, User moderator,  String reason) {
		EmbedBuilder embed = new EmbedBuilder();
		embed.setAuthor("You have been unmuted in " + guild.getName(), null, guild.getIconUrl());
		embed.setTimestamp(Instant.now());
		embed.addField("Channel", channel == null ? "All" : channel.getAsMention(), false);
		embed.addField("Moderator", moderator.getAsTag() + " (" + moderator.getId() + ")", false);
		embed.addField("Reason", reason == null ? "None Given" : reason, false);
		return embed.build();
	}
	
	public static MessageEmbed getWarnEmbed(Guild guild, User moderator, List<Map<String, Object>> warnConfig, long warning, boolean punishments, String action, String reason) {
		String nextActionString = "Warn";
		if (punishments == true) {
			Map<String, Object> nextAction = getWarning(warnConfig, warning + 1);
			if (nextAction != null) {
				String actionData = (String) nextAction.get("action");
				if (nextAction.containsKey("time")) {
					nextActionString = GeneralUtils.title(actionData) + " (" + TimeUtils.toTimeString(nextAction.get("time") instanceof Integer ? (int) nextAction.get("time") : (long) nextAction.get("time"), ChronoUnit.SECONDS) + ")";
				} else {
					nextActionString = GeneralUtils.title(actionData);
				}
			}
		}
		
		EmbedBuilder embed = new EmbedBuilder();
		embed.setAuthor("You have been " + action + " in " + guild.getName() + " (" + GeneralUtils.getNumberSuffix(Math.toIntExact(warning)) + " Warning)", null, guild.getIconUrl());
		embed.setTimestamp(Instant.now());
		embed.addField("Moderator", moderator.getAsTag() + " (" + moderator.getId() + ")", false);
		embed.addField("Reason", reason == null ? "None Given" : reason, false);
		embed.addField("Next Action", nextActionString, false);
		return embed.build();
	}
	
	public static Map<String, Object> getWarning(List<Map<String, Object>> warnConfig, long warning) {
		if (warnConfig.isEmpty()) {
			warnConfig = DEFAULT_WARN_CONFIG;
		}
		
		if (getMaxWarning(warnConfig) + 1 <= warning) {
			return getWarning(warnConfig, 1);
		}
		
		long warningNumber;
		for (Map<String, Object> warn : warnConfig) {
			warningNumber = warn.get("warning") instanceof Integer ? (int) warn.get("warning") : (long) warn.get("warning");
			if (warningNumber == warning) {
				return warn;
			}
		}
		
		return null; //should only happen when the warning is a warn
	}
	
	public static long getMaxWarning(List<Map<String, Object>> warnConfig) {
		if (warnConfig.isEmpty()) {
			warnConfig = DEFAULT_WARN_CONFIG;
		}
		
		long maxWarning = 0;
		long warningNumber;
		for (Map<String, Object> warn : warnConfig) {
			warningNumber = warn.get("warning") instanceof Integer ? (int) warn.get("warning") : (long) warn.get("warning");
			if (maxWarning == 0) {
				maxWarning = warningNumber;
			} else {
				if (warningNumber > maxWarning) {
					maxWarning = warningNumber;
				}
			}
		}
		
		return maxWarning;
	}
	
	public static List<Map<String, Object>> getMuteData(String memberId, List<Map<String, Object>> users, long muteLength) {
		long timestamp = Clock.systemUTC().instant().getEpochSecond();
		for (Map<String, Object> userData : users) {
			if (userData.get("id").equals(memberId)) {
				users.remove(userData);
				userData.put("id", memberId);
				userData.put("time", timestamp);
				userData.put("amount", muteLength);
				users.add(userData);
				
				return users;
			}
		}
		
		Map<String, Object> userData = new HashMap<>();
		userData.put("id", memberId);
		userData.put("time", timestamp);
		userData.put("amount", muteLength);
		users.add(userData);
		
		return users;
	}
	
	public static void createModLog(Guild guild, Connection connection, User moderator, User user, String action, String reason) {
		Get data = r.table("modlogs").get(guild.getId()); 
		Map<String, Object> dataRan = data.run(connection);
		
		if (dataRan == null) {
			return;
		}
		
		if ((boolean) dataRan.get("toggle") == false) {
			return;
		}
		
		TextChannel channel;
		String channelData = (String) dataRan.get("channel");
		if (channelData == null) {
			channel = null;
		} else {
			channel = guild.getTextChannelById(channelData);
		}
		
		if (channel == null) {
			return;
		}
		
		long caseNumber = ((long) dataRan.get("case#")) + 1;
		String defaultMod = "Unknown (Update using `modlog case " + caseNumber + " <reason>`)";
		String defaultReason = "None (Update using `modlog case " + caseNumber + " <reason>`)";
		
		EmbedBuilder embed = new EmbedBuilder();
		embed.setTitle("Case " + caseNumber + " | " + action);
		embed.setTimestamp(Instant.now());
		embed.addField("User", user.getAsTag(), false);
		embed.addField("Moderator", moderator == null ? defaultMod : moderator.getAsTag(), false);
		embed.addField("Reason", reason == null ? defaultReason : reason, false);
		
		Map<String, Object> newCase = new HashMap<String, Object>();
		if (guild.getSelfMember().hasPermission(channel, Permission.MESSAGE_EMBED_LINKS, Permission.MESSAGE_WRITE, Permission.MESSAGE_READ)) {
			channel.sendMessage(embed.build()).queue(message -> {	
				newCase.put("id", caseNumber);
				newCase.put("action", action);
				newCase.put("reason", reason);
				newCase.put("mod", moderator == null ? null : moderator.getId());
				newCase.put("user", user.getId());
				newCase.put("time", Clock.systemUTC().instant().getEpochSecond());
				newCase.put("message", message.getId());
				
				data.update(row -> r.hashMap("case", row.g("case").append(newCase)).with("case#", row.g("case#").add(1))).runNoReply(connection);
			});
		} else {
			newCase.put("id", caseNumber);
			newCase.put("action", action);
			newCase.put("reason", reason);
			newCase.put("mod", moderator == null ? null : moderator.getId());
			newCase.put("user", user.getId());
			newCase.put("time", Clock.systemUTC().instant().getEpochSecond());
			newCase.put("message", null);
			
			data.update(row -> r.hashMap("case", row.g("case").append(newCase)).with("case#", row.g("case#").add(1))).runNoReply(connection);
		}
	}
	
	public static void createOffence(Guild guild, Connection connection, User moderator, User user, String action, String reason) {
		r.table("offence").insert(r.hashMap("id", user.getId()).with("offences", new Object[0])).run(connection, OptArgs.of("durability", "soft"));
		Get data = r.table("offence").get(user.getId());
		
		Map<String, Object> newOffence = new HashMap<String, Object>();
		newOffence.put("mod", moderator == null ? null : moderator.getId());
		newOffence.put("time", Clock.systemUTC().instant().getEpochSecond());
		newOffence.put("proof", null);
		newOffence.put("server", guild.getId());
		newOffence.put("action", action.replace("(Automatic)", ""));
		newOffence.put("reason", reason);
		
		data.update(row -> r.hashMap("offences", row.g("offences").append(newOffence))).runNoReply(connection);
	}
	
	public static void createModLogAndOffence(Guild guild, Connection connection, User moderator, User user, String action, String reason) {
		createModLog(guild, connection, moderator, user, action, reason);
		createOffence(guild, connection, moderator, user, action, reason);
	}
	
	public static List<String> getPrefixes(Guild guild, User user) {
		return ModUtils.getPrefixes(guild, user, Settings.DATABASE_NAME);
	}
	
	public static List<String> getPrefixes(Guild guild, User user, String databaseName) {
		List<String> userPrefixes = r.db(databaseName).table("prefix").get(user.getId()).getField("prefixes").default_(new String[0]).run(Sx4Bot.getConnection());
		List<String> serverPrefixes = guild == null ? new ArrayList<>() : r.db(databaseName).table("prefix").get(guild.getId()).getField("prefixes").default_(new String[0]).run(Sx4Bot.getConnection());
		
		if (userPrefixes.isEmpty() && serverPrefixes.isEmpty()) {
			return Sx4Bot.getCommandListener().getDefaultPrefixes();
		} else if (!userPrefixes.isEmpty()) {
			return userPrefixes;
		} else if (!serverPrefixes.isEmpty()) {
			return serverPrefixes;
		} else {
			return Sx4Bot.getCommandListener().getDefaultPrefixes();
		}
	}

}
