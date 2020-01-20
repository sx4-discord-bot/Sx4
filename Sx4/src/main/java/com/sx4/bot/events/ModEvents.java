package com.sx4.bot.events;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.bson.Document;
import org.bson.conversions.Bson;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.WriteModel;
import com.sx4.bot.database.Database;
import com.sx4.bot.utils.ModAction;
import com.sx4.bot.utils.ModUtils;
import com.sx4.bot.utils.WarnUtils;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.audit.ActionType;
import net.dv8tion.jda.api.audit.AuditLogEntry;
import net.dv8tion.jda.api.audit.AuditLogKey;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.channel.text.TextChannelCreateEvent;
import net.dv8tion.jda.api.events.guild.GuildBanEvent;
import net.dv8tion.jda.api.events.guild.GuildUnbanEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberLeaveEvent;
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
	
	public void onGuildMemberLeave(GuildMemberLeaveEvent event) {
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
		Document allData = Database.get().getGuildById(event.getGuild().getIdLong(), null, Projections.include("mute.users", "mute.role", "mute.leaveAction", "warn.configuration", "warn.users", "warn.punishments"));
		Document data = allData.get("mute", Database.EMPTY_DOCUMENT);
		Document warnData = allData.get("warn", Database.EMPTY_DOCUMENT);
		
		if (!data.isEmpty()) {
			long timestampNow = Clock.systemUTC().instant().getEpochSecond();
			
			List<Document> users = data.getList("users", Document.class, Collections.emptyList());
			for (Document userData : users) {
				if (userData.getLong("id") == event.getMember().getIdLong()) {
					Long duration = userData.getLong("duration");
					if (duration != null) {
						long timeLeft = userData.getLong("timestamp") + duration - timestampNow;
						if (timeLeft > 0) {
							Document leaveAction = data.get("leaveAction", Database.EMPTY_DOCUMENT);
							Long actionDuration = leaveAction.getLong("duration");
							ModAction action = ModAction.getFromType(leaveAction.getInteger("type", ModAction.MUTE.getType()));
							
							Long roleId = data.getLong("role");
							Role role = roleId != null ? event.getGuild().getRoleById(roleId) : null;
							switch (action) {
								case WARN:
									WarnUtils.handleWarning(allData, event.getGuild(), event.getMember(), event.getGuild().getSelfMember(), "Mute Evasion", (warning, exception) -> {
										if (exception != null) {
											exception.printStackTrace();
										} else {
											List<Document> warnConfiguration = warnData.getList("configuration", Document.class, Collections.emptyList());
											if (warnConfiguration.isEmpty()) {
												warnConfiguration = ModUtils.DEFAULT_WARN_CONFIGURATION;
											}
											
											Long muteDuration = warning.getDuration();
											
											List<WriteModel<Document>> bulkData = new ArrayList<>();
											if (warning.getAction() == ModAction.MUTE) {
												bulkData.add(ModUtils.getMuteUpdate(event.getGuild().getIdLong(), event.getMember().getIdLong(), users, muteDuration));
											} else if (warning.getAction() == ModAction.MUTE_EXTEND) {
												bulkData.add(ModUtils.getMuteUpdate(event.getGuild().getIdLong(), event.getMember().getIdLong(), users, muteDuration, true));
											}
											
											bulkData.add(WarnUtils.getUserUpdate(warnData.getList("users", Document.class, Collections.emptyList()), warnConfiguration, event.getGuild().getIdLong(), event.getMember().getIdLong(), "Mute Evasion"));
											
											Database.get().bulkWriteGuilds(bulkData, (result, writeException) -> {
												if (writeException != null) {
													writeException.printStackTrace();
												}
											});
										}
									});
									
									break;
								case MUTE:
									if (role != null) {
										if (actionDuration != null) {
											UpdateOptions options = new UpdateOptions().arrayFilters(List.of(Filters.eq("user.id", event.getMember().getIdLong())));
											Database.get().updateGuildById(event.getGuild().getIdLong(), null, Updates.set("mute.users.$[user].duration", actionDuration), options, (result, exception) -> {
												if (exception != null) {
													exception.printStackTrace();
												} else {
													MuteEvents.cancelExecutor(event.getGuild().getIdLong(), event.getMember().getIdLong());
													
													ScheduledFuture<?> executor = MuteEvents.scheduledExectuor.schedule(() -> MuteEvents.removeUserMute(event.getGuild().getIdLong(), event.getMember().getIdLong(), roleId), actionDuration, TimeUnit.SECONDS);
													MuteEvents.putExecutor(event.getGuild().getIdLong(), event.getMember().getIdLong(), executor);
												}
											});
										}
										
										event.getGuild().addRoleToMember(event.getMember(), role).queue();
									}
									
									break;
								case MUTE_EXTEND:
									if (role != null) {
										if (actionDuration != null) {
											UpdateOptions options = new UpdateOptions().arrayFilters(List.of(Filters.eq("user.id", event.getMember().getIdLong())));
											Database.get().updateGuildById(event.getGuild().getIdLong(), null, Updates.inc("mute.users.$[user].duration", actionDuration), options, (result, exception) -> {
												if (exception != null) {
													exception.printStackTrace();
												} else {
													ScheduledFuture<?> currentExecutor = MuteEvents.getExecutor(event.getGuild().getIdLong(), event.getMember().getIdLong());
													
													long delay = actionDuration;
													if (currentExecutor != null && !currentExecutor.isDone()) {
														currentExecutor.cancel(true);
														delay += currentExecutor.getDelay(TimeUnit.SECONDS);
													}
													
													ScheduledFuture<?> executor = MuteEvents.scheduledExectuor.schedule(() -> MuteEvents.removeUserMute(event.getGuild().getIdLong(), event.getMember().getIdLong(), roleId), delay, TimeUnit.SECONDS);
													MuteEvents.putExecutor(event.getGuild().getIdLong(), event.getMember().getIdLong(), executor);
												}
											});
										}
										
										event.getGuild().addRoleToMember(event.getMember(), role).queue();
									}
									
									break;
								case KICK:
									event.getMember().kick().queue();
									
									break;
								case BAN:
									event.getMember().ban(1).queue();
									
									break;
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
		Long roleId = Database.get().getGuildById(event.getGuild().getIdLong(), null, Projections.include("mute.role")).getEmbedded(List.of("mute", "role"), Long.class);	
		if (roleId != null) {
			if (event.getRoles().stream().map(Role::getIdLong).anyMatch(id -> id == roleId)) {
				if (event.getGuild().getSelfMember().hasPermission(Permission.VIEW_AUDIT_LOGS)) {
					event.getGuild().retrieveAuditLogs().type(ActionType.MEMBER_ROLE_UPDATE).queueAfter(500, TimeUnit.MILLISECONDS, auditLogs -> {
						AuditLogEntry entry = auditLogs.stream()
								.filter(e -> e.getTargetIdLong() == event.getUser().getIdLong())
								.filter(e -> e.getChangeByKey(AuditLogKey.MEMBER_ROLES_ADD) != null)
								.filter(e -> {
									List<Map<String, String>> roleEntries = e.getChangeByKey(AuditLogKey.MEMBER_ROLES_ADD).getNewValue();
									List<String> roleIds = roleEntries.stream().map(roleEntry -> roleEntry.get("id")).collect(Collectors.toList());
									
									for (Role role : event.getRoles()) {
										if (!roleIds.contains(role.getId())) {
											return false;
										}
									}
									
									return true;
								})
								.findFirst()
								.orElse(null);
						
						User moderator = entry.getUser();
						
						if (moderator != null && !moderator.equals(event.getJDA().getSelfUser())) {
							ModUtils.createModLogAndOffence(event.getGuild(), moderator, event.getUser(), "Mute (Infinite)", entry.getReason());
							
							Bson update = Updates.combine(
								Updates.set("mute.users.$[user].duration", null),
								Updates.set("mute.users.$[user].timestamp", Clock.systemUTC().instant().getEpochSecond())
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
		Long roleId = Database.get().getGuildById(event.getGuild().getIdLong(), null, Projections.include("mute.role")).getEmbedded(List.of("mute", "role"), Long.class);	
		if (roleId != null) {
			if (event.getRoles().stream().map(Role::getIdLong).anyMatch(id -> id == roleId)) {
				if (event.getGuild().getSelfMember().hasPermission(Permission.VIEW_AUDIT_LOGS)) {
					event.getGuild().retrieveAuditLogs().type(ActionType.MEMBER_ROLE_UPDATE).queueAfter(500, TimeUnit.MILLISECONDS, auditLogs -> {
						AuditLogEntry entry = auditLogs.stream()
								.filter(e -> e.getTargetIdLong() == event.getUser().getIdLong())
								.filter(e -> e.getChangeByKey(AuditLogKey.MEMBER_ROLES_REMOVE) != null)
								.filter(e -> {
									List<Map<String, String>> roleEntries = e.getChangeByKey(AuditLogKey.MEMBER_ROLES_REMOVE).getNewValue();
									List<String> roleIds = roleEntries.stream().map(roleEntry -> roleEntry.get("id")).collect(Collectors.toList());
									
									for (Role role : event.getRoles()) {
										if (!roleIds.contains(role.getId())) {
											return false;
										}
									}
									
									return true;
								})
								.findFirst()
								.orElse(null);
						
						User moderator = entry.getUser();
						
						if (moderator != null && !moderator.equals(event.getJDA().getSelfUser())) {
							ModUtils.createModLog(event.getGuild(), moderator, event.getUser(), "Unmute", entry.getReason());

							Database.get().updateGuildById(event.getGuild().getIdLong(), Updates.pull("mute.users", Filters.eq("id", event.getUser().getIdLong())), (result, exception) -> {
								if (exception != null) {
									exception.printStackTrace();
								}
							});
							
							MuteEvents.cancelExecutor(event.getGuild().getIdLong(), event.getMember().getIdLong());
						}
					});
				}
			}
		}
	}
	
	public void onTextChannelCreate(TextChannelCreateEvent event) {
		Document data = Database.get().getGuildById(event.getGuild().getIdLong(), null, Projections.include("mute.role", "mute.autoUpdate")).get("mute", Database.EMPTY_DOCUMENT);
		Long roleId = data.getLong("role");
		Role role = roleId != null ? event.getGuild().getRoleById(roleId) : null;
		
		if (data.getBoolean("autoUpdate", true) && role != null) {
			event.getChannel().putPermissionOverride(role).setDeny(Permission.MESSAGE_WRITE).queue();
		}
	}
	
}
