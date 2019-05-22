package com.sx4.events;

import static com.rethinkdb.RethinkDB.r;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import com.rethinkdb.net.Connection;
import com.rethinkdb.net.Cursor;
import com.sx4.core.Sx4Bot;
import com.sx4.utils.ModUtils;

import net.dv8tion.jda.bot.sharding.ShardManager;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Member;
import net.dv8tion.jda.core.entities.Role;
import net.dv8tion.jda.core.entities.User;
import net.dv8tion.jda.core.events.role.RoleDeleteEvent;
import net.dv8tion.jda.core.hooks.ListenerAdapter;

public class MuteEvents extends ListenerAdapter {
	
	public static Map<String, String> muteRoles = new HashMap<>();
	
	public static Map<String, Map<String, ScheduledFuture<?>>> executors = new HashMap<>();
	
	public static ScheduledExecutorService scheduledExectuor = Executors.newSingleThreadScheduledExecutor();
	
	public void onRoleDelete(RoleDeleteEvent event) {
		if (muteRoles.containsKey(event.getGuild().getId())) {
			if (event.getRole().getId().equals(muteRoles.get(event.getGuild().getId()))) {
				muteRoles.remove(event.getGuild().getId());
			}
		}
	}
	
	public static void putMuteRole(String guildId, String roleId) {
		muteRoles.put(guildId, roleId);
	}
	
	public static Role getMuteRole(Guild guild) {
		if (muteRoles.containsKey(guild.getId())) {
			Role role = guild.getRoleById(muteRoles.get(guild.getId()));
			return role;
		} else {
			return null;
		}
	}
	
	@SuppressWarnings("unchecked")
	public static void ensureMuteRoles() {
		ShardManager shardManager = Sx4Bot.getShardManager();
		Connection connection = Sx4Bot.getConnection();
		Cursor<Map<String, Object>> cursor = r.table("mute").run(connection);
		List<Map<String, Object>> data = cursor.toList();
		
		User selfUser = shardManager.getApplicationInfo().getJDA().getSelfUser();
		long timestampNow = Clock.systemUTC().instant().getEpochSecond();
		for (Map<String, Object> guildData : data) {
			Guild guild = shardManager.getGuildById((String) guildData.get("id"));
			if (guild != null) {
				Role muteRole = null;
				for (Role role : guild.getRoles()) {
					if (role.getName().equals("Muted - " + selfUser.getName())) {
						MuteEvents.putMuteRole(guild.getId(), role.getId());
						muteRole = role;
						break;
					}
				}
				
				if (muteRole != null) {
					List<Map<String, Object>> users = (List<Map<String, Object>>) guildData.get("users");
					List<String> userIds = new ArrayList<>();
					for (Map<String, Object> userData : users) {
						userIds.add((String) userData.get("id"));
					}
					
					List<Member> mutedMembers = guild.getMembersWithRoles(muteRole);
					List<String> mutedMemberIds = new ArrayList<>();
					for (Member member : mutedMembers) {
						mutedMemberIds.add(member.getUser().getId());
						if (userIds.contains(member.getUser().getId())) {
							continue;
						} else {
							r.table("mute").get(guild.getId()).update(row -> r.hashMap("users", row.g("users").append(r.hashMap("id", member.getUser().getId()).with("time", timestampNow).with("amount", null)))).runNoReply(connection);
							ModUtils.createModLogAndOffence(guild, connection, selfUser, member.getUser(), "Mute (Infinite)", "Mute role was added while the bot was offline");
						}
					}
					
					for (Map<String, Object> userData : users) {
						if (mutedMemberIds.contains(userData.get("id"))) {
							continue;
						} else {
							Member unmutedMember = guild.getMemberById((String) userData.get("id"));
							MuteEvents.cancelExecutor(guild.getId(), unmutedMember.getUser().getId());
							r.table("mute").get(guild.getId()).update(row -> r.hashMap("users", row.g("users").filter(d -> d.g("id").ne(unmutedMember.getUser().getId())))).runNoReply(connection);
							ModUtils.createModLog(guild, connection, selfUser, unmutedMember.getUser(), "Unmute", "Mute role was removed while the bot was offline");
						}
					}
				} else {
					r.table("mute").get(guild.getId()).update(r.hashMap("users", new Object[0])).runNoReply(connection);
				}
			}
		}
	}
	
	public static void putExecutor(String guildId, String userId, ScheduledFuture<?> executor) {
		Map<String, ScheduledFuture<?>> userExecutors = executors.containsKey(guildId) ? executors.get(guildId) : new HashMap<>();
		userExecutors.put(userId, executor);
		executors.put(guildId, userExecutors);
	}
	
	public static boolean cancelExecutor(String guildId, String userId) {
		if (executors.containsKey(guildId)) {
			Map<String, ScheduledFuture<?>> userExecutors = executors.get(guildId);
			if (userExecutors.containsKey(userId)) {
				ScheduledFuture<?> executor = userExecutors.get(userId);
				if (!executor.isDone()) {
					executor.cancel(false);
				}
				
				userExecutors.remove(userId);
				executors.put(guildId, userExecutors);
				
				return true;
			}
		}
		
		return false;
	}
	
	
	public static void removeUserMute(Member member, Role muteRole) {
		Connection connection = Sx4Bot.getConnection();
		Guild guild = member.getGuild();
		User selfUser = guild.getSelfMember().getUser();
		
		if (guild.getMember(member.getUser()) == null) {
			return;
		}
		
		if (muteRole == null) {
			muteRole = MuteEvents.getMuteRole(guild);
		}
		
		if (muteRole != null) {
			if (member.getRoles().contains(muteRole)) {
				guild.getController().removeSingleRoleFromMember(member, muteRole).queue();
			}
		}
		
		r.table("mute").get(guild.getId()).update(row -> r.hashMap("users", row.g("users").filter(d -> d.g("id").ne(member.getUser().getId())))).runNoReply(connection);
		ModUtils.createModLog(guild, connection, selfUser, member.getUser(), "Unmute (Automatic)", "Time Limit Served");
		member.getUser().openPrivateChannel().queue(channel -> channel.sendMessage(ModUtils.getUnmuteEmbed(guild, null, selfUser, "Time Limit Served")).queue(), e -> {});
		
		MuteEvents.cancelExecutor(guild.getId(), member.getUser().getId());
	}
	
	public static void removeUserMute(Member member) {
		MuteEvents.removeUserMute(member, null);
	}
	
	@SuppressWarnings("unchecked")
	public static void ensureMutes() {
		ShardManager shardManager = Sx4Bot.getShardManager();
		Cursor<Map<String, Object>> cursor = r.table("mute").run(Sx4Bot.getConnection());
		List<Map<String, Object>> data = cursor.toList();
		
		long timestampNow = Clock.systemUTC().instant().getEpochSecond();
		for (Map<String, Object> guildData : data) {
			Guild guild = shardManager.getGuildById((String) guildData.get("id"));
			if (guild != null) {
				List<Map<String, Object>> users = (List<Map<String, Object>>) guildData.get("users");
				for (Map<String, Object> userData : users) {
					if (userData.get("amount") != null) {
						Member member = guild.getMemberById((String) userData.get("id"));
						if (member != null) {
							long timeLeft = ((long) userData.get("time") + (long) userData.get("amount")) - timestampNow;
							if (timeLeft <= 0) {
								MuteEvents.removeUserMute(member);
							} else {
								ScheduledFuture<?> executor = MuteEvents.scheduledExectuor.schedule(() -> MuteEvents.removeUserMute(member), timeLeft, TimeUnit.SECONDS);
								MuteEvents.putExecutor(guild.getId(), member.getUser().getId(), executor);
							}
				
						}
					}
				}
			}
		}
	}
	
}
