package com.sx4.bot.events;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.bson.Document;
import org.bson.conversions.Bson;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.sx4.bot.database.Database;
import com.sx4.bot.utils.ModUtils;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.audit.ActionType;
import net.dv8tion.jda.api.audit.AuditLogEntry;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.channel.text.TextChannelCreateEvent;
import net.dv8tion.jda.api.events.channel.voice.VoiceChannelCreateEvent;
import net.dv8tion.jda.api.events.guild.GuildBanEvent;
import net.dv8tion.jda.api.events.guild.GuildUnbanEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleAddEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleRemoveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class ModEvents extends ListenerAdapter {

	public void onGuildBan(GuildBanEvent event) {
		if (event.getGuild().getSelfMember().hasPermission(Permission.VIEW_AUDIT_LOGS)) {
			event.getGuild().retrieveAuditLogs().type(ActionType.BAN).queueAfter(500, TimeUnit.MILLISECONDS, auditLogs -> {
				User moderator = null;
				String reason = null;
				for (AuditLogEntry auditLog : auditLogs) {
					if (auditLog.getTargetIdLong() == event.getUser().getIdLong()) {
						moderator = auditLog.getUser();
						reason = auditLog.getReason();
						break;
					}
				}
				
				if (moderator != null && !moderator.equals(event.getJDA().getSelfUser())) {
					ModUtils.createModLogAndOffence(event.getGuild(), moderator, event.getUser(), "Ban", reason);
				}
			});
		}
	}
	
	public void onGuildUnban(GuildUnbanEvent event) {
		if (event.getGuild().getSelfMember().hasPermission(Permission.VIEW_AUDIT_LOGS)) {
			event.getGuild().retrieveAuditLogs().type(ActionType.UNBAN).queueAfter(500, TimeUnit.MILLISECONDS, auditLogs -> {
				User moderator = null;
				String reason = null;
				for (AuditLogEntry auditLog : auditLogs) {
					if (auditLog.getTargetIdLong() == event.getUser().getIdLong()) {
						moderator = auditLog.getUser();
						reason = auditLog.getReason();
						break;
					}
				}
				
				if (moderator != null && !moderator.equals(event.getJDA().getSelfUser())) {
					ModUtils.createModLog(event.getGuild(), moderator, event.getUser(), "Unban", reason);
				}
			});
		}
	}
	
	public void onGuildMemberRemove(GuildMemberRemoveEvent event) {
		if (event.getGuild().getSelfMember().hasPermission(Permission.VIEW_AUDIT_LOGS)) {
			event.getGuild().retrieveAuditLogs().type(ActionType.KICK).queueAfter(500, TimeUnit.MILLISECONDS, auditLogs -> {
				User moderator = null;
				String reason = null;
				for (AuditLogEntry auditLog : auditLogs) {
					if (auditLog.getTargetIdLong() == event.getUser().getIdLong() && LocalDateTime.now(ZoneOffset.UTC).toEpochSecond(ZoneOffset.UTC) - auditLog.getTimeCreated().toEpochSecond() <= 10) {
						moderator = auditLog.getUser();
						reason = auditLog.getReason();
						break;
					}
				}
				
				if (moderator != null && !moderator.equals(event.getJDA().getSelfUser())) {
					ModUtils.createModLogAndOffence(event.getGuild(), moderator, event.getUser(), "Kick", reason);
				}
			});
		}
	}
	
	public void onGuildMemberJoin(GuildMemberJoinEvent event) {
		Document data = Database.get().getGuildById(event.getGuild().getIdLong(), null, Projections.include("mute.users")).get("mute", Database.EMPTY_DOCUMENT);
		if (!data.isEmpty()) {
			long timestampNow = Clock.systemUTC().instant().getEpochSecond();
			
			List<Document> users = data.getList("users", Document.class, Collections.emptyList());
			for (Document userData : users) {
				if (userData.getLong("id") == event.getMember().getUser().getIdLong()) {
					Long duration = userData.getLong("duration");
					if (duration != null) {
						long timeLeft = userData.getLong("timestamp") + duration - timestampNow;
						if (timeLeft > 0) {
							Role mutedRole = MuteEvents.getMuteRole(event.getGuild());
							if (mutedRole != null) {
								event.getGuild().addRoleToMember(event.getMember(), mutedRole).queue();
							}
						} else {
							MuteEvents.removeUserMute(event.getGuild().getIdLong(), event.getMember().getIdLong());
						}
					}
				}
			}
		}
	}
	
	public void onGuildMemberRoleAdd(GuildMemberRoleAddEvent event) {
		Role mutedRole = MuteEvents.getMuteRole(event.getGuild());
		if (mutedRole != null) {
			if (event.getRoles().contains(mutedRole)) {
				if (event.getGuild().getSelfMember().hasPermission(Permission.VIEW_AUDIT_LOGS)) {
					event.getGuild().retrieveAuditLogs().type(ActionType.MEMBER_ROLE_UPDATE).queueAfter(500, TimeUnit.MILLISECONDS, auditLogs -> {
						long timestampNow = LocalDateTime.now(ZoneOffset.UTC).toEpochSecond(ZoneOffset.UTC);
						User moderator = null;
						String reason = null;
						for (AuditLogEntry auditLog : auditLogs) {
							if (auditLog.getTargetIdLong() == event.getUser().getIdLong() && timestampNow - auditLog.getTimeCreated().toEpochSecond() <= 5) {
								moderator = auditLog.getUser();
								reason = auditLog.getReason();
								break;
							}
						}
						
						if (moderator == null || !moderator.equals(event.getJDA().getSelfUser())) {
							ModUtils.createModLogAndOffence(event.getGuild(), moderator, event.getUser(), "Mute (Infinite)", reason);
							
							Bson update = Updates.combine(
								Updates.set("mute.users.$[user].duration", null),
								Updates.set("mute.users.$[user].timestamp", timestampNow)
							);
							
							UpdateOptions updateOptions = new UpdateOptions().arrayFilters(List.of(Filters.eq("user.id", event.getUser().getIdLong()))).upsert(true);
							
							Database.get().updateGuildById(event.getGuild().getIdLong(), null, update, updateOptions, (result, exception) -> {
								if (exception != null) {
									exception.printStackTrace();
								}
							});
						}
					});
				}
			}
		}
	}
	
	public void onGuildMemberRoleRemove(GuildMemberRoleRemoveEvent event) {
		Role mutedRole = MuteEvents.getMuteRole(event.getGuild());
		if (mutedRole != null) {
			if (event.getRoles().contains(mutedRole)) {
				if (event.getGuild().getSelfMember().hasPermission(Permission.VIEW_AUDIT_LOGS)) {
					event.getGuild().retrieveAuditLogs().type(ActionType.MEMBER_ROLE_UPDATE).queueAfter(500, TimeUnit.MILLISECONDS, auditLogs -> {
						long timestampNow = LocalDateTime.now(ZoneOffset.UTC).toEpochSecond(ZoneOffset.UTC);
						User moderator = null;
						String reason = null;
						for (AuditLogEntry auditLog : auditLogs) {
							if (auditLog.getTargetIdLong() == event.getUser().getIdLong() && timestampNow - auditLog.getTimeCreated().toEpochSecond() <= 5) {
								moderator = auditLog.getUser();
								reason = auditLog.getReason();
								break;
							}
						}
						
						if (moderator != null && !moderator.equals(event.getJDA().getSelfUser())) {
							ModUtils.createModLog(event.getGuild(), moderator, event.getUser(), "Unmute", reason);

							Database.get().updateGuildById(event.getGuild().getIdLong(), Updates.pull("mute.users", Filters.eq("id", event.getUser().getIdLong())), (result, exception) -> {
								if (exception != null) {
									exception.printStackTrace();
								}
							});
							
							MuteEvents.cancelExecutor(event.getGuild().getIdLong(), event.getMember().getUser().getIdLong());
						}
					});
				}
			}
		}
	}
	
	public void onTextChannelCreate(TextChannelCreateEvent event) {
		Role mutedRole = MuteEvents.getMuteRole(event.getGuild());
		if (mutedRole != null) {
			event.getChannel().putPermissionOverride(mutedRole).setDeny(Permission.MESSAGE_WRITE).queue();
		}
	}
	
	public void onVoiceChannelCreate(VoiceChannelCreateEvent event) {
		Role mutedRole = MuteEvents.getMuteRole(event.getGuild());
		if (mutedRole != null) {
			event.getChannel().putPermissionOverride(mutedRole).setDeny(Permission.VOICE_SPEAK).queue();
		}
	}
	
}
