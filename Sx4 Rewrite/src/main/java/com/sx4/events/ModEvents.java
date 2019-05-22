package com.sx4.events;

import static com.rethinkdb.RethinkDB.r;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.rethinkdb.gen.ast.Get;
import com.sx4.core.Sx4Bot;
import com.sx4.utils.ModUtils;

import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.audit.ActionType;
import net.dv8tion.jda.core.audit.AuditLogEntry;
import net.dv8tion.jda.core.entities.Role;
import net.dv8tion.jda.core.entities.User;
import net.dv8tion.jda.core.events.channel.text.TextChannelCreateEvent;
import net.dv8tion.jda.core.events.channel.voice.VoiceChannelCreateEvent;
import net.dv8tion.jda.core.events.guild.GuildBanEvent;
import net.dv8tion.jda.core.events.guild.GuildUnbanEvent;
import net.dv8tion.jda.core.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.core.events.guild.member.GuildMemberLeaveEvent;
import net.dv8tion.jda.core.events.guild.member.GuildMemberRoleAddEvent;
import net.dv8tion.jda.core.events.guild.member.GuildMemberRoleRemoveEvent;
import net.dv8tion.jda.core.hooks.ListenerAdapter;

public class ModEvents extends ListenerAdapter {

	public void onGuildBan(GuildBanEvent event) {
		if (event.getGuild().getSelfMember().hasPermission(Permission.VIEW_AUDIT_LOGS)) {
			event.getGuild().getAuditLogs().type(ActionType.BAN).queueAfter(500, TimeUnit.MILLISECONDS, auditLogs -> {
				User moderator = null;
				String reason = null;
				for (AuditLogEntry auditLog : auditLogs) {
					if (auditLog.getTargetId().equals(event.getUser().getId())) {
						moderator = auditLog.getUser();
						reason = auditLog.getReason();
						break;
					}
				}
				
				if (!moderator.equals(event.getJDA().getSelfUser())) {
					ModUtils.createModLogAndOffence(event.getGuild(), Sx4Bot.getConnection(), moderator, event.getUser(), "Ban", reason);
				}
			});
		}
	}
	
	public void onGuildUnban(GuildUnbanEvent event) {
		if (event.getGuild().getSelfMember().hasPermission(Permission.VIEW_AUDIT_LOGS)) {
			event.getGuild().getAuditLogs().type(ActionType.UNBAN).queueAfter(500, TimeUnit.MILLISECONDS, auditLogs -> {
				User moderator = null;
				String reason = null;
				for (AuditLogEntry auditLog : auditLogs) {
					if (auditLog.getTargetId().equals(event.getUser().getId())) {
						moderator = auditLog.getUser();
						reason = auditLog.getReason();
						break;
					}
				}
				
				if (!moderator.equals(event.getJDA().getSelfUser())) {
					ModUtils.createModLog(event.getGuild(), Sx4Bot.getConnection(), moderator, event.getUser(), "Unban", reason);
				}
			});
		}
	}
	
	public void onGuildMemberLeave(GuildMemberLeaveEvent event) {
		if (event.getGuild().getSelfMember().hasPermission(Permission.VIEW_AUDIT_LOGS)) {
			event.getGuild().getAuditLogs().type(ActionType.KICK).queueAfter(500, TimeUnit.MILLISECONDS, auditLogs -> {
				User moderator = null;
				String reason = null;
				for (AuditLogEntry auditLog : auditLogs) {
					if (auditLog.getTargetId().equals(event.getUser().getId()) && LocalDateTime.now(ZoneOffset.UTC).toEpochSecond(ZoneOffset.UTC) - auditLog.getCreationTime().toEpochSecond() <= 10) {
						moderator = auditLog.getUser();
						reason = auditLog.getReason();
						break;
					}
				}
				
				if (moderator != null && !moderator.equals(event.getJDA().getSelfUser())) {
					ModUtils.createModLogAndOffence(event.getGuild(), Sx4Bot.getConnection(), moderator, event.getUser(), "Kick", reason);
				}
			});
		}
	}
	
	@SuppressWarnings("unchecked")
	public void onGuildMemberJoin(GuildMemberJoinEvent event) {
		Map<String, Object> data = r.table("mute").get(event.getGuild().getId()).run(Sx4Bot.getConnection());
		if (data != null) {
			List<Map<String, Object>> users = (List<Map<String, Object>>) data.get("users");
			for (Map<String, Object> userData : users) {
				if (userData.get("amount") != null) {
					long timeLeft = ((long) userData.get("time") + (long) userData.get("amount")) - Clock.systemUTC().instant().getEpochSecond();
					if (timeLeft > 0) {
						if (userData.get("id").equals(event.getMember().getUser().getId())) {
							Role mutedRole = null;
							for (Role role : event.getGuild().getRoles()) {
								if (role.getName().equals("Muted - " + event.getJDA().getSelfUser().getName())) {
									mutedRole = role;
								}
							}
							
							if (mutedRole != null) {
								event.getGuild().getController().addSingleRoleToMember(event.getMember(), mutedRole).queue();
							}
						}
					} else {
						MuteEvents.removeUserMute(event.getMember());
					}
				}
			}
		}
	}
	
	public void onGuildMemberRoleAdd(GuildMemberRoleAddEvent event) {
		Get data = r.table("mute").get(event.getGuild().getId());
		Map<String, Object> dataRan = data.run(Sx4Bot.getConnection());
		if (dataRan == null) {
			return;
		}
		
		Role mutedRole = null;
		for (Role role : event.getGuild().getRoles()) {
			if (role.getName().equals("Muted - " + event.getJDA().getSelfUser().getName())) {
				mutedRole = role;
			}
		}
		
		if (mutedRole != null) {
			if (event.getRoles().get(0).equals(mutedRole)) {
				if (event.getGuild().getSelfMember().hasPermission(Permission.VIEW_AUDIT_LOGS)) {
					event.getGuild().getAuditLogs().type(ActionType.MEMBER_ROLE_UPDATE).queueAfter(500, TimeUnit.MILLISECONDS, auditLogs -> {
						long timestampNow = LocalDateTime.now(ZoneOffset.UTC).toEpochSecond(ZoneOffset.UTC);
						User moderator = null;
						String reason = null;
						for (AuditLogEntry auditLog : auditLogs) {
							if (auditLog.getTargetId().equals(event.getUser().getId()) && timestampNow - auditLog.getCreationTime().toEpochSecond() <= 5) {
								moderator = auditLog.getUser();
								reason = auditLog.getReason();
								break;
							}
						}
						
						if (!moderator.equals(event.getJDA().getSelfUser())) {
							ModUtils.createModLogAndOffence(event.getGuild(), Sx4Bot.getConnection(), moderator, event.getUser(), "Mute (Infinite)", reason);
							data.update(row -> r.hashMap("users", row.g("users").append(r.hashMap("id", event.getUser().getId()).with("amount", null).with("time", timestampNow)))).runNoReply(Sx4Bot.getConnection());
						}
					});
				}
			}
		}
	}
	
	public void onGuildMemberRoleRemove(GuildMemberRoleRemoveEvent event) {
		Get data = r.table("mute").get(event.getGuild().getId());
		Map<String, Object> dataRan = data.run(Sx4Bot.getConnection());
		if (dataRan == null) {
			return;
		}
		
		Role mutedRole = null;
		for (Role role : event.getGuild().getRoles()) {
			if (role.getName().equals("Muted - " + event.getJDA().getSelfUser().getName())) {
				mutedRole = role;
			}
		}
		
		if (mutedRole != null) {
			if (event.getRoles().get(0).equals(mutedRole)) {
				if (event.getGuild().getSelfMember().hasPermission(Permission.VIEW_AUDIT_LOGS)) {
					event.getGuild().getAuditLogs().type(ActionType.MEMBER_ROLE_UPDATE).queueAfter(500, TimeUnit.MILLISECONDS, auditLogs -> {
						long timestampNow = LocalDateTime.now(ZoneOffset.UTC).toEpochSecond(ZoneOffset.UTC);
						User moderator = null;
						String reason = null;
						for (AuditLogEntry auditLog : auditLogs) {
							if (auditLog.getTargetId().equals(event.getUser().getId()) && timestampNow - auditLog.getCreationTime().toEpochSecond() <= 5) {
								moderator = auditLog.getUser();
								reason = auditLog.getReason();
								break;
							}
						}
						
						if (!moderator.equals(event.getJDA().getSelfUser())) {
							ModUtils.createModLog(event.getGuild(), Sx4Bot.getConnection(), moderator, event.getUser(), "Unmute", reason);
							data.update(row -> r.hashMap("users", row.g("users").filter(d -> d.g("id").ne(event.getUser().getId())))).runNoReply(Sx4Bot.getConnection());
							MuteEvents.cancelExecutor(event.getGuild().getId(), event.getMember().getUser().getId());
						}
					});
				}
			}
		}
	}
	
	public void onTextChannelCreate(TextChannelCreateEvent event) {
		Role mutedRole = null;
		for (Role role : event.getGuild().getRoles()) {
			if (role.getName().equals("Muted - " + event.getJDA().getSelfUser().getName())) {
				mutedRole = role;
			}
		}
		
		if (mutedRole != null) {
			event.getChannel().putPermissionOverride(mutedRole).setDeny(Permission.MESSAGE_WRITE).queue();
		}
	}
	
	public void onVoiceChannelCreate(VoiceChannelCreateEvent event) {
		Role mutedRole = null;
		for (Role role : event.getGuild().getRoles()) {
			if (role.getName().equals("Muted - " + event.getJDA().getSelfUser().getName())) {
				mutedRole = role;
			}
		}
		
		if (mutedRole != null) {
			event.getChannel().putPermissionOverride(mutedRole).setDeny(Permission.VOICE_SPEAK).queue();
		}
	}
	
}
