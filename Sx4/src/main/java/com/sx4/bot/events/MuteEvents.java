package com.sx4.bot.events;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.bson.Document;
import org.bson.conversions.Bson;

import com.mongodb.client.FindIterable;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.WriteModel;
import com.sx4.bot.core.Sx4Bot;
import com.sx4.bot.database.Database;
import com.sx4.bot.utils.ModUtils;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.role.RoleDeleteEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.sharding.ShardManager;

public class MuteEvents extends ListenerAdapter {
	
	public static Map<Long, Long> muteRoles = new HashMap<>();
	
	public static Map<Long, Map<Long, ScheduledFuture<?>>> executors = new HashMap<>();
	
	public static ScheduledExecutorService scheduledExectuor = Executors.newSingleThreadScheduledExecutor();
	
	public void onRoleDelete(RoleDeleteEvent event) {
		if (muteRoles.containsKey(event.getGuild().getIdLong())) {
			if (event.getRole().getIdLong() == muteRoles.get(event.getGuild().getIdLong())) {
				muteRoles.remove(event.getGuild().getIdLong());
			}
		}
	}
	
	public static void putMuteRole(long guildId, long roleId) {
		muteRoles.put(guildId, roleId);
	}
	
	public static Role getMuteRole(Guild guild) {
		if (muteRoles.containsKey(guild.getIdLong())) {
			Role role = guild.getRoleById(muteRoles.get(guild.getIdLong()));
			return role;
		} else {
			return null;
		}
	}
	
	public static void ensureMuteRoles() {
		ShardManager shardManager = Sx4Bot.getShardManager();
		FindIterable<Document> allData = Database.get().getGuilds().find().projection(Projections.include("mute.users"));
		allData.forEach((Document data) -> {
			Document muteData = data.get("mute", Database.EMPTY_DOCUMENT);
			User selfUser = shardManager.getShardById(0).getSelfUser();
			long timestampNow = Clock.systemUTC().instant().getEpochSecond();
			Guild guild = shardManager.getGuildById(data.getLong("_id"));
			if (guild != null) {
				Role muteRole = null;
				for (Role role : guild.getRoles()) {
					if (role.getName().equals("Muted - " + selfUser.getName())) {
						MuteEvents.putMuteRole(guild.getIdLong(), role.getIdLong());
						muteRole = role;
						break;
					}
				}
				
				if (muteRole != null) {
					List<Document> users = muteData.getList("users", Document.class, Collections.emptyList());
					List<Long> userIds = new ArrayList<>();
					for (Document userData : users) {
						userIds.add(userData.getLong("id"));
					}
					
					List<Member> mutedMembers = guild.getMembersWithRoles(muteRole);
					List<Long> mutedMemberIds = new ArrayList<>();
					for (Member member : mutedMembers) {
						mutedMemberIds.add(member.getUser().getIdLong());
						if (userIds.contains(member.getUser().getIdLong())) {
							continue;
						} else {
							Bson update = Updates.combine(
									Updates.set("mute.users.$[user].duration", null),
									Updates.set("mute.users.$[user].timestamp", timestampNow)
							);
							
							UpdateOptions updateOptions = new UpdateOptions().arrayFilters(List.of(Filters.eq("user.id", member.getIdLong()))).upsert(true);
							
							Database.get().updateGuildById(guild.getIdLong(), null, update, updateOptions, (result, exception) -> {
								if (exception != null) {
									exception.printStackTrace();
								}
							});
							
							ModUtils.createModLogAndOffence(guild, selfUser, member.getUser(), "Mute (Infinite)", "Mute role was added while the bot was offline");
						}
					}
					
					for (Document userData : users) {
						if (mutedMemberIds.contains(userData.getLong("id"))) {
							continue;
						} else {
							Member unmutedMember = guild.getMemberById(userData.getLong("id"));
							if (unmutedMember != null) {
								MuteEvents.cancelExecutor(guild.getIdLong(), unmutedMember.getUser().getIdLong());
								
								Database.get().updateGuildById(guild.getIdLong(), Updates.pull("mute.users", Filters.eq("id", unmutedMember.getIdLong())), (result, exception) -> {
									if (exception != null) {
										exception.printStackTrace();
									}
								});
								
								ModUtils.createModLog(guild, selfUser, unmutedMember.getUser(), "Unmute", "Mute role was removed while the bot was offline");
							}
						}
					}
				} else {
					Database.get().updateGuildById(guild.getIdLong(), Updates.unset("mute.users"), (result, exception) -> {
						if (exception != null) {
							exception.printStackTrace();
						}
					});
				}
			}
		});
	}
	
	public static void putExecutor(long guildId, long userId, ScheduledFuture<?> executor) {
		Map<Long, ScheduledFuture<?>> userExecutors = executors.containsKey(guildId) ? executors.get(guildId) : new HashMap<>();
		userExecutors.put(userId, executor);
		executors.put(guildId, userExecutors);
	}
	
	public static boolean cancelExecutor(long guildId, long userId) {
		if (executors.containsKey(guildId)) {
			Map<Long, ScheduledFuture<?>> userExecutors = executors.get(guildId);
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
	
	public static UpdateOneModel<Document> removeUserMuteAndGet(long guildId, long userId, Long roleId) {
		Guild guild = Sx4Bot.getShardManager().getGuildById(guildId);
		if (guild != null) {
			Role muteRole = roleId == null ? null : guild.getRoleById(roleId);
			Member member = guild.getMemberById(userId);
			if (member != null) {
				User selfUser = guild.getSelfMember().getUser();
				
				if (guild.getMember(member.getUser()) != null) {
					if (muteRole == null) {
						muteRole = MuteEvents.getMuteRole(guild);
					}
					
					if (muteRole != null && member.getRoles().contains(muteRole)) {
						if (guild.getSelfMember().hasPermission(Permission.MANAGE_ROLES) && guild.getSelfMember().canInteract(muteRole)) {
							guild.removeRoleFromMember(member, muteRole).queue();
						} else {
							return null;
						}
					}
					
					ModUtils.createModLog(guild, selfUser, member.getUser(), "Unmute (Automatic)", "Time Limit Served");
					member.getUser().openPrivateChannel().queue(channel -> channel.sendMessage(ModUtils.getUnmuteEmbed(guild, null, selfUser, "Time Limit Served")).queue(), e -> {});
					
					MuteEvents.cancelExecutor(guild.getIdLong(), member.getUser().getIdLong());
					
					return new UpdateOneModel<>(Filters.eq("_id", guild.getIdLong()), Updates.pull("mute.users", Filters.eq("id", member.getIdLong())));
				}
			}
		}
		
		return null;
	}
	
	public static UpdateOneModel<Document> removeUserMuteAndGet(long guildId, long userId) {
		return MuteEvents.removeUserMuteAndGet(guildId, userId, null);
	}
	
	public static void removeUserMute(long guildId, long userId, Long roleId) {
		Database.get().updateGuildById(MuteEvents.removeUserMuteAndGet(guildId, userId, roleId), (result, exception) -> {
			if (exception != null) {
				exception.printStackTrace();
			}
		});
	}
	
	public static void removeUserMute(long guildId, long userId) {
		MuteEvents.removeUserMute(guildId, userId, null);
	}
	
	public static void ensureMutes() {
		ShardManager shardManager = Sx4Bot.getShardManager();
		FindIterable<Document> allData = Database.get().getGuilds().find(Filters.exists("mute.users")).projection(Projections.include("mute.users"));
		
		List<WriteModel<Document>> bulkData = new ArrayList<>();
		for (Document data : allData) {
			try {
				Document guildData = data.get("mute", Database.EMPTY_DOCUMENT);
				long timestampNow = Clock.systemUTC().instant().getEpochSecond();
				Guild guild = shardManager.getGuildById(data.getLong("_id"));
				if (guild != null) {
					List<Document> users = guildData.getList("users", Document.class, Collections.emptyList());
					for (Document userData : users) {
						Long duration = userData.getLong("duration");
						if (duration != null) {
							Member member = guild.getMemberById(userData.getLong("id"));
							if (member != null) {
								long timeLeft = userData.getLong("timestamp") + duration - timestampNow;
								if (timeLeft <= 0) {
									UpdateOneModel<Document> update = MuteEvents.removeUserMuteAndGet(guild.getIdLong(), member.getIdLong());
									if (update != null) {
										bulkData.add(update);
									}
								} else {
									ScheduledFuture<?> executor = MuteEvents.scheduledExectuor.schedule(() -> MuteEvents.removeUserMute(guild.getIdLong(), member.getIdLong()), timeLeft, TimeUnit.SECONDS);
									MuteEvents.putExecutor(guild.getIdLong(), member.getUser().getIdLong(), executor);
								}
							}
						}
					}
				}
			} catch(Throwable e) {
				e.printStackTrace();
			}
		}
		
		if (!bulkData.isEmpty()) {
			Database.get().bulkWriteGuilds(bulkData, (result, exception) -> {
				if (exception != null) {
					exception.printStackTrace();
				}
			});
		}
	}
	
}
