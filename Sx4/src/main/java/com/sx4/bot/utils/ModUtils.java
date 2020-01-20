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
import com.sx4.bot.utils.WarnUtils.Warning;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.VoiceChannel;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.internal.utils.PermissionUtil;

public class ModUtils {
	
	public static final List<Document> DEFAULT_WARN_CONFIGURATION = new ArrayList<>();
	
	static {
		DEFAULT_WARN_CONFIGURATION.add(new Document("action", ModAction.MUTE.getType()).append("warning", 2).append("duration", 1800L));
		DEFAULT_WARN_CONFIGURATION.add(new Document("action", ModAction.KICK.getType()).append("warning", 3));
		DEFAULT_WARN_CONFIGURATION.add(new Document("action", ModAction.BAN.getType()).append("warning", 4));
	}
	
	private static void createMuteRole(Guild guild, boolean autoUpdate, BiConsumer<Role, String> muteRole) {
		if (guild.getRoles().size() >= 250) {
			muteRole.accept(null, ErrorResponse.MAX_ROLES_PER_GUILD.getMeaning());
			return;
		}
		
		guild.createRole().setName("Muted - " + guild.getJDA().getSelfUser().getName()).queue(role -> {
			Database.get().updateGuildById(guild.getIdLong(), Updates.set("mute.role", role.getIdLong()), (result, exception) -> {
				if (exception != null) {
					muteRole.accept(null, exception.getMessage());
				} else {
					muteRole.accept(role, null);
					if (autoUpdate && guild.getSelfMember().hasPermission(Permission.MANAGE_PERMISSIONS)) {
						guild.getTextChannels().forEach(channel -> channel.upsertPermissionOverride(role).deny(Permission.MESSAGE_WRITE).queue(null, e -> {}));
					}
				}
			});
		});
	}
	
	public static void getOrCreateMuteRole(Guild guild, Long roleId, boolean autoUpdate, BiConsumer<Role, String> muteRole) {
		if (roleId == null) {
			ModUtils.createMuteRole(guild, autoUpdate, muteRole);
		} else {
			Role role = guild.getRoleById(roleId);
			if (role != null) {
				muteRole.accept(role, null);
			} else {
				ModUtils.createMuteRole(guild, autoUpdate, muteRole);
			}
		}
	}
	
	public static void getOrCreateMuteRole(Guild guild, BiConsumer<Role, String> muteRole) {
		Document data = Database.get().getGuildById(guild.getIdLong(), null, Projections.include("mute.role", "mute.autoUpdate")).get("mute", Database.EMPTY_DOCUMENT);
		Long roleId = data.getLong("role");
		boolean autoUpdate = data.getBoolean("autoUpdate", true);
		
		if (roleId == null) {
			ModUtils.createMuteRole(guild, autoUpdate, muteRole);
		} else {
			Role role = guild.getRoleById(roleId);
			if (role != null) {
				muteRole.accept(role, null);
			} else {
				ModUtils.createMuteRole(guild, autoUpdate, muteRole);
			}
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
				ModAction actionData = nextAction.getAction();
				if (nextAction.hasDuration()) {
					nextActionString = actionData.getName() + " (" + TimeUtils.toTimeString(nextAction.getDuration(), ChronoUnit.SECONDS) + ")";
				} else {
					nextActionString = actionData.getName();
				}
			}
		}
		
		EmbedBuilder embed = new EmbedBuilder();
		embed.setAuthor("You have received a " + warning.getAction().getName() + " in " + guild.getName() + " (" + GeneralUtils.getNumberSuffix(warning.getWarning()) + " Warning)", null, guild.getIconUrl());
		embed.setTimestamp(Instant.now());
		embed.addField("Moderator", moderator.getAsTag() + " (" + moderator.getId() + ")", false);
		embed.addField("Reason", reason == null ? "None Given" : GeneralUtils.limitString(reason, MessageEmbed.VALUE_MAX_LENGTH), false);
		embed.addField("Next Action", nextActionString, false);
		return embed.build();
	}
	
	public static UpdateOneModel<Document> getMuteUpdate(long guildId, long memberId, List<Document> users, Long muteLength) {
		return ModUtils.getMuteUpdate(guildId, memberId, users, muteLength, false);
	}
	
	public static UpdateOneModel<Document> getMuteUpdate(long guildId, long memberId, List<Document> users, Long muteLength, boolean extend) {
		long timestamp = Clock.systemUTC().instant().getEpochSecond();
		for (Document userData : users) {
			if (userData.getLong("id") == memberId) {
				Bson update = Updates.combine(Updates.set("mute.users.$[user].timestamp", timestamp), extend ? Updates.inc("mute.users.$[user].duration", muteLength) : Updates.set("mute.users.$[user].duration", muteLength));
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
