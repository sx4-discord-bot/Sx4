package com.sx4.bot.utils;


import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.function.BiConsumer;

import org.bson.Document;
import org.bson.conversions.Bson;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.sx4.bot.core.Sx4Bot;
import com.sx4.bot.database.Database;
import com.sx4.bot.events.MuteEvents;
import com.sx4.bot.utils.WarnUtils.Warning;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.PermissionOverride;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.VoiceChannel;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.internal.utils.PermissionUtil;

public class ModUtils {
	
	public static List<Document> defaultWarnConfiguration = new ArrayList<>();
	
	static {
		Document secondWarning = new Document().append("action", "mute").append("warning", 2).append("duration", 1800L);
		defaultWarnConfiguration.add(secondWarning);
		
		Document thirdWarning = new Document().append("action", "kick").append("warning", 3);
		defaultWarnConfiguration.add(thirdWarning);
		
		Document fourthWarning = new Document().append("action", "ban").append("warning", 4);
		defaultWarnConfiguration.add(fourthWarning);
	}
	
	public static void getOrCreateMuteRole(Guild guild, BiConsumer<Role, String> muteRole) {
		String roleName = "Muted - " + guild.getSelfMember().getUser().getName();
		
		Role role = MuteEvents.getMuteRole(guild);
		if (role != null) {
			muteRole.accept(role, null);

			if (guild.getSelfMember().hasPermission(Permission.MANAGE_PERMISSIONS)) {
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
			}
		} else {
			for (Role guildRole : guild.getRoles()) {
				if (guildRole.getName().equals(roleName)) {
					muteRole.accept(guildRole, null);
					MuteEvents.putMuteRole(guild.getIdLong(), guildRole.getIdLong());
					
					if (guild.getSelfMember().hasPermission(Permission.MANAGE_PERMISSIONS)) {
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
					}
					
					return;
				}
			}
			
			if (guild.getRoles().size() >= 250) {		
				muteRole.accept(null, "I cannot create the mute role because the server has the max amount of roles (250)");
				return;
			}
			
			guild.createRole().setName(roleName).queue(newRole -> {
				muteRole.accept(newRole, null);
				MuteEvents.putMuteRole(guild.getIdLong(), newRole.getIdLong());
				
				if (guild.getSelfMember().hasPermission(Permission.MANAGE_PERMISSIONS)) {
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
				}
				
				return;
			});
		}
	}
	
	public static boolean canConnect(Member member, VoiceChannel voiceChannel) {
		EnumSet<Permission> perms = Permission.getPermissions(PermissionUtil.getEffectivePermission(voiceChannel, member));
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
		embed.addField("Reason", reason == null ? "None Given" : GeneralUtils.limitString(reason, MessageEmbed.VALUE_MAX_LENGTH), false);
		return embed.build();
	}
	
	public static MessageEmbed getBanEmbed(Guild guild, User moderator, String reason) {
		EmbedBuilder embed = new EmbedBuilder();
		embed.setAuthor("You have been banned from " + guild.getName(), null, guild.getIconUrl());
		embed.setTimestamp(Instant.now());
		embed.addField("Moderator", moderator.getAsTag() + " (" + moderator.getId() + ")", false);
		embed.addField("Reason", reason == null ? "None Given" : GeneralUtils.limitString(reason, MessageEmbed.VALUE_MAX_LENGTH), false);
		return embed.build();
	}
	
	public static MessageEmbed getMuteEmbed(Guild guild, TextChannel channel, User moderator, long length, String reason) {
		EmbedBuilder embed = new EmbedBuilder();
		embed.setAuthor("You have been muted in " + guild.getName(), null, guild.getIconUrl());
		embed.setTimestamp(Instant.now());
		embed.addField("Channel", channel == null ? "All" : channel.getAsMention(), false);
		embed.addField("Length", length == 0 ? "Infinite" : TimeUtils.toTimeString(length, ChronoUnit.SECONDS), false);
		embed.addField("Moderator", moderator.getAsTag() + " (" + moderator.getId() + ")", false);
		embed.addField("Reason", reason == null ? "None Given" : GeneralUtils.limitString(reason, MessageEmbed.VALUE_MAX_LENGTH), false);
		return embed.build();
	}
	
	public static MessageEmbed getUnmuteEmbed(Guild guild, TextChannel channel, User moderator,  String reason) {
		EmbedBuilder embed = new EmbedBuilder();
		embed.setAuthor("You have been unmuted in " + guild.getName(), null, guild.getIconUrl());
		embed.setTimestamp(Instant.now());
		embed.addField("Channel", channel == null ? "All" : channel.getAsMention(), false);
		embed.addField("Moderator", moderator.getAsTag() + " (" + moderator.getId() + ")", false);
		embed.addField("Reason", reason == null ? "None Given" : GeneralUtils.limitString(reason, MessageEmbed.VALUE_MAX_LENGTH), false);
		return embed.build();
	}
	
	public static MessageEmbed getWarnEmbed(Guild guild, User moderator, boolean punishments, List<Document> warnConfiguration, Warning warning, String reason) {
		String nextActionString = "Warn";
		if (punishments) {
			Warning nextAction = WarnUtils.getWarning(warnConfiguration, warning.getWarning() + 1);
			if (nextAction != null) {
				String actionData = nextAction.getAction();
				if (nextAction.hasDuration()) {
					nextActionString = GeneralUtils.title(actionData) + " (" + TimeUtils.toTimeString(nextAction.getDuration(), ChronoUnit.SECONDS) + ")";
				} else {
					nextActionString = GeneralUtils.title(actionData);
				}
			}
		}
		
		EmbedBuilder embed = new EmbedBuilder();
		embed.setAuthor("You have been " + WarnUtils.getSuffixedAction(warning.getAction()) + " in " + guild.getName() + " (" + GeneralUtils.getNumberSuffix(warning.getWarning()) + " Warning)", null, guild.getIconUrl());
		embed.setTimestamp(Instant.now());
		embed.addField("Moderator", moderator.getAsTag() + " (" + moderator.getId() + ")", false);
		embed.addField("Reason", reason == null ? "None Given" : GeneralUtils.limitString(reason, MessageEmbed.VALUE_MAX_LENGTH), false);
		embed.addField("Next Action", nextActionString, false);
		return embed.build();
	}
	
	public static UpdateOneModel<Document> getMuteUpdate(long guildId, long memberId, List<Document> users, Long muteLength) {
		long timestamp = Clock.systemUTC().instant().getEpochSecond();
		for (Document userData : users) {
			if (userData.getLong("id") == memberId) {
				Bson update = Updates.combine(Updates.set("mute.users.$[user].timestamp", timestamp), Updates.set("mute.users.$[user].duration", muteLength));
				UpdateOptions updateOptions = new UpdateOptions().arrayFilters(List.of(Filters.eq("user.id", memberId)));
				return new UpdateOneModel<>(Filters.eq("_id", guildId), update, updateOptions);
			}
		}
		
		Document userData = new Document("id", memberId)
				.append("duration", muteLength)
				.append("timestamp", timestamp);

		return new UpdateOneModel<>(Filters.eq("_id", guildId), Updates.push("mute.users", userData), new UpdateOptions().upsert(true));
	}
	
	public static void createModLog(Guild guild, User moderator, User user, String action, String reason) {
		Document data = Database.get().getGuildById(guild.getIdLong(), null, Projections.include("modlog.enabled", "modlog.channelId", "modlog.caseAmount")).get("modlog", Database.EMPTY_DOCUMENT);
		if (!data.getBoolean("enabled", false)) {
			return;
		}
		
		Long channelId = data.getLong("channelId");
		TextChannel channel = channelId == null ? null : guild.getTextChannelById(channelId);
		if (channel == null) {
			return;
		}
		
		int caseNumber = data.getInteger("caseAmount", 0) + 1;
		
		String defaultMod = "Unknown (Update using `modlog case " + caseNumber + " <reason>`)";
		String defaultReason = "None (Update using `modlog case " + caseNumber + " <reason>`)";
		
		EmbedBuilder embed = new EmbedBuilder();
		embed.setTitle("Case " + caseNumber + " | " + action);
		embed.setTimestamp(Instant.now());
		embed.addField("User", user.getAsTag(), false);
		embed.addField("Moderator", moderator == null ? defaultMod : moderator.getAsTag(), false);
		embed.addField("Reason", reason == null ? defaultReason : reason, false);
		
		Document modlogData = new Document("id", caseNumber)
				.append("action", action)
				.append("reason", reason)
				.append("moderatorId", moderator == null ? null : moderator.getIdLong())
				.append("userId", user.getIdLong())
				.append("guildId", guild.getIdLong())
				.append("timestamp", Clock.systemUTC().instant().getEpochSecond());
		
		if (guild.getSelfMember().hasPermission(channel, Permission.MESSAGE_EMBED_LINKS, Permission.MESSAGE_WRITE, Permission.MESSAGE_READ)) {
			channel.sendMessage(embed.build()).queue(message -> {	
				modlogData.append("messageId", message.getIdLong());
				
				Database.get().insertModLogCase(modlogData, (result, exception) -> {
					if (exception != null) {
						exception.printStackTrace();
					}
				});

				Database.get().updateGuildById(guild.getIdLong(), Updates.inc("modlog.caseAmount", 1), (result, exception) -> {
					if (exception != null) {
						exception.printStackTrace();
					}
				});
			});
		} else {
			modlogData.append("messageId", null);
			
			Database.get().insertModLogCase(modlogData, (result, exception) -> {
				if (exception != null) {
					exception.printStackTrace();
				}
			});

			Database.get().updateGuildById(guild.getIdLong(), Updates.inc("modlog.caseAmount", 1), (result, exception) -> {
				if (exception != null) {
					exception.printStackTrace();
				}
			});
		}
	}
	
	public static void createOffence(Guild guild, User moderator, User user, String action, String reason) {
		Document offenceData = new Document("moderatorId", moderator == null ? null : moderator.getIdLong())
				.append("timestamp", Clock.systemUTC().instant().getEpochSecond())
				.append("guildId", guild.getIdLong())
				.append("action", action.replace("(Automatic)", ""))
				.append("reason", reason);
				
		Database.get().updateUserById(user.getIdLong(), Updates.push("offences", offenceData), (result, exception) -> {
			if (exception != null) {
				exception.printStackTrace();
			}
		});
	}
	
	public static void createModLogAndOffence(Guild guild, User moderator, User user, String action, String reason) {
		ModUtils.createModLog(guild, moderator, user, action, reason);
		ModUtils.createOffence(guild, moderator, user, action, reason);
	}
	
	public static List<String> getPrefixes(Guild guild, User user) {
		return ModUtils.getPrefixes(guild, user, true);
	}
	
	public static List<String> getPrefixes(Guild guild, User user, boolean current) {
		Database database = Database.get();
		
		MongoCollection<Document> currentUserCollection = current ? database.getUsers() : database.getOtherUsers();
		MongoCollection<Document> currentGuildCollection = current ? database.getGuilds() : database.getOtherGuilds();
		
		Bson projection = Projections.include("prefixes");
		
		Document userPrefixesData = currentUserCollection.find(Filters.eq("_id", user.getIdLong())).projection(projection).first();
		Document serverPrefixesData = null;
		if (guild != null) {
			serverPrefixesData = currentGuildCollection.find(Filters.eq("_id", guild.getIdLong())).projection(projection).first();
		}
		
		List<String> userPrefixes = userPrefixesData == null ? Collections.emptyList() : userPrefixesData.getList("prefixes", String.class, Collections.emptyList());
		List<String> serverPrefixes = serverPrefixesData == null ? Collections.emptyList() : serverPrefixesData.getList("prefixes", String.class, Collections.emptyList());
		
		if (!userPrefixes.isEmpty()) {
			return userPrefixes;
		} else if (!serverPrefixes.isEmpty()) {
			return serverPrefixes;
		} else {
			return Sx4Bot.getCommandListener().getDefaultPrefixes();
		}
	}

}
