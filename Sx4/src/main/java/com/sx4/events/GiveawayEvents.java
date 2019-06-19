package com.sx4.events;

import static com.rethinkdb.RethinkDB.r;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import com.rethinkdb.net.Connection;
import com.rethinkdb.net.Cursor;
import com.sx4.core.Sx4Bot;
import com.sx4.core.Sx4CommandEventListener;
import com.sx4.settings.Settings;
import com.sx4.utils.GiveawayUtils;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.sharding.ShardManager;

public class GiveawayEvents {

	public static Map<String, Map<Long, ScheduledFuture<?>>> executors = new HashMap<>();
	
	public static ScheduledExecutorService scheduledExectuor = Executors.newSingleThreadScheduledExecutor();
	
	public static void putExecutor(String guildId, long id, ScheduledFuture<?> executor) {
		Map<Long, ScheduledFuture<?>> userExecutors = executors.containsKey(guildId) ? executors.get(guildId) : new HashMap<>();
		userExecutors.put(id, executor);
		executors.put(guildId, userExecutors);
	}
	
	public static boolean cancelExecutor(String guildId, long id) {
		if (executors.containsKey(guildId)) {
			Map<Long, ScheduledFuture<?>> userExecutors = executors.get(guildId);
			if (userExecutors.containsKey(id)) {
				ScheduledFuture<?> executor = userExecutors.get(id);
				if (!executor.isDone()) {
					executor.cancel(false);
				}
				
				userExecutors.remove(id);
				executors.put(guildId, userExecutors);
				
				return true;
			}
		}
		
		return false;
	}
	
	public static void removeGiveaway(Guild guild, Map<String, Object> data) {
		Connection connection = Sx4Bot.getConnection();
		long giveawayId = (long) data.get("id");
		TextChannel channel = guild.getTextChannelById((String) data.get("channel"));
		
		if (channel != null) {
			channel.retrieveMessageById((String) data.get("message")).queue(message -> {
				for (MessageReaction reaction : message.getReactions()) {
					if (reaction.getReactionEmote().getName().equals("🎉")) {
						List<Member> members = new ArrayList<>();
						CompletableFuture<?> future = reaction.retrieveUsers().forEachAsync((user) -> {
							Member reactionMember = guild.getMember(user);
							if (reactionMember != null && !members.contains(reactionMember) && reactionMember != guild.getSelfMember()) {
								members.add(reactionMember);
							}
							
							return true;
						});
							
						future.thenRun(() -> {
							if (members.size() == 0) {
								channel.sendMessage("No one entered the giveaway, the giveaway has been cancelled :no_entry:").queue();
								message.delete().queue(null, e -> {});
							} else {
								Set<Member> winners = GiveawayUtils.getRandomSample(members, Math.min(members.size(), Math.toIntExact((long) data.get("winners"))));
								List<String> winnerMentions = new ArrayList<>(), winnerTags = new ArrayList<>();
								for (Member winner : winners) {
									winnerMentions.add(winner.getAsMention());
									winnerTags.add(winner.getUser().getAsTag());
								}
								
								channel.sendMessage(String.join(", ", winnerMentions) + ", Congratulations you have won the giveaway for **" + ((String) data.get("item")) + "**").queue();
								
								EmbedBuilder embed = new EmbedBuilder();
								embed.setTitle("Giveaway");
								embed.setDescription("**" + String.join(", ", winnerTags) + "** has won **" + ((String) data.get("item")) + "**");
								embed.setFooter("Giveaway Ended", null);
								message.editMessage(embed.build()).queue();
							}
								
							r.table("giveaway").get(guild.getId()).update(row -> r.hashMap("giveaways", row.g("giveaways").filter(d -> d.g("id").ne(giveawayId)))).runNoReply(connection);
							GiveawayEvents.cancelExecutor(guild.getId(), giveawayId);
						}).exceptionally(e -> {
							e.printStackTrace();
							Sx4CommandEventListener.sendErrorMessage(Sx4Bot.getShardManager().getGuildById(Settings.SUPPORT_SERVER_ID).getTextChannelById(Settings.ERRORS_CHANNEL_ID), e, new Object[0]);
							return null;
						});
					}
				}
			}, e -> {
				if (e instanceof ErrorResponseException) {
					ErrorResponseException exception = (ErrorResponseException) e;
					if (exception.getErrorCode() == 10008) {
						r.table("giveaway").get(guild.getId()).update(row -> r.hashMap("giveaways", row.g("giveaways").filter(d -> d.g("id").ne(giveawayId)))).runNoReply(connection);
						GiveawayEvents.cancelExecutor(guild.getId(), giveawayId);
					}
				}
			});
		}
	}
	
	@SuppressWarnings("unchecked")
	public static void ensureGiveaways() {
		ShardManager shardManager = Sx4Bot.getShardManager();
		try (Cursor<Map<String, Object>> cursor = r.table("giveaway").run(Sx4Bot.getConnection())) {
			List<Map<String, Object>> data = cursor.toList();
			
			long timestampNow = Clock.systemUTC().instant().getEpochSecond();
			for (Map<String, Object> guildData : data) {
				Guild guild = shardManager.getGuildById((String) guildData.get("id"));
				if (guild != null) {
					List<Map<String, Object>> giveaways = (List<Map<String, Object>>) guildData.get("giveaways");
					for (Map<String, Object> giveaway : giveaways) {
						long timeLeft = (long) giveaway.get("endtime") - timestampNow;
						if (timeLeft <= 0) {
							GiveawayEvents.removeGiveaway(guild, giveaway);
						} else {
							ScheduledFuture<?> executor = GiveawayEvents.scheduledExectuor.schedule(() -> GiveawayEvents.removeGiveaway(guild, giveaway), timeLeft, TimeUnit.SECONDS);
							GiveawayEvents.putExecutor(guild.getId(), (long) giveaway.get("id"), executor);
						}
					}
				}
			}
		}
	}
	
}
