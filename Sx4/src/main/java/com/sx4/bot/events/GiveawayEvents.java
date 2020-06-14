package com.sx4.bot.events;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.bson.Document;

import com.mongodb.client.FindIterable;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.WriteModel;
import com.sx4.bot.core.Sx4Bot;
import com.sx4.bot.core.Sx4CommandEventListener;
import com.sx4.bot.database.Database;
import com.sx4.bot.settings.Settings;
import com.sx4.bot.utils.GiveawayUtils;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message.MentionType;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.sharding.ShardManager;

public class GiveawayEvents {

	public static Map<Long, Map<Integer, ScheduledFuture<?>>> executors = new HashMap<>();
	
	public static ScheduledExecutorService scheduledExectuor = Executors.newSingleThreadScheduledExecutor();
	
	public static void putExecutor(long guildId, int id, ScheduledFuture<?> executor) {
		Map<Integer, ScheduledFuture<?>> userExecutors = executors.containsKey(guildId) ? executors.get(guildId) : new HashMap<>();
		userExecutors.put(id, executor);
		executors.put(guildId, userExecutors);
	}
	
	public static boolean cancelExecutor(long guildId, int id) {
		if (executors.containsKey(guildId)) {
			Map<Integer, ScheduledFuture<?>> userExecutors = executors.get(guildId);
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
	
	public static UpdateOneModel<Document> removeGiveawayAndGet(long guildId, Document data) {
		Guild guild = Sx4Bot.getShardManager().getGuildById(guildId);
		int giveawayId = data.getInteger("id");
		if (guild != null) {
			TextChannel channel = guild.getTextChannelById(data.getLong("channelId"));
			if (channel != null) {
				channel.retrieveMessageById(data.getLong("messageId")).queue(message -> {
					for (MessageReaction reaction : message.getReactions()) {
						if (reaction.getReactionEmote().getName().equals("🎉")) {
							List<Member> members = new ArrayList<>();
							CompletableFuture<?> future = reaction.retrieveUsers().forEachAsync((user) -> {
								Member reactionMember = guild.getMember(user);
								if (reactionMember != null && reactionMember != guild.getSelfMember()) {
									members.add(reactionMember);
								}
								
								return true;
							});
								
							future.thenRun(() -> {
								if (members.size() == 0) {
									channel.sendMessage("No one entered the giveaway, the giveaway has been cancelled :no_entry:").queue();
									message.delete().queue(null, e -> {});
								} else {
									Set<Member> winners = GiveawayUtils.getRandomSample(members, Math.min(members.size(), data.getInteger("winnersAmount")));
									List<String> winnerMentions = new ArrayList<>(), winnerTags = new ArrayList<>();
									for (Member winner : winners) {
										winnerMentions.add(winner.getAsMention());
										winnerTags.add(winner.getUser().getAsTag());
									}
									
									channel.sendMessage(String.join(", ", winnerMentions) + ", Congratulations you have won the giveaway for **" + data.getString("item") + "**").allowedMentions(EnumSet.of(MentionType.USER)).queue();
									
									EmbedBuilder embed = new EmbedBuilder();
									embed.setTitle("Giveaway");
									embed.setDescription("**" + String.join(", ", winnerTags) + "** has won **" + data.getString("item") + "**");
									embed.setFooter("Giveaway Ended", null);
									message.editMessage(embed.build()).queue();
								}
							}).exceptionally(e -> {
								e.printStackTrace();
								Sx4CommandEventListener.sendErrorMessage(Sx4Bot.getShardManager().getGuildById(Settings.SUPPORT_SERVER_ID).getTextChannelById(Settings.ERRORS_CHANNEL_ID), e, new Object[0]);
								return null;
							});
						}
					}
				}, e -> {});
			}
		}
		
		GiveawayEvents.cancelExecutor(guildId, giveawayId);
		
		return new UpdateOneModel<>(Filters.eq("_id", guildId), Updates.pull("giveaway.giveaways", Filters.eq("id", giveawayId)));
	}
	
	public static void removeGiveaway(long guildId, Document data) {
		Database.get().updateGuildById(GiveawayEvents.removeGiveawayAndGet(guildId, data), (result, exception) -> {
			if (exception != null) {
				exception.printStackTrace();
			}
		});
	}
	
	public static void ensureGiveaways() {
		ShardManager shardManager = Sx4Bot.getShardManager();
		FindIterable<Document> allData = Database.get().getGuilds().find(Filters.exists("giveaway.giveaways")).projection(Projections.include("giveaway.giveaways"));
		
		List<WriteModel<Document>> bulkData = new ArrayList<>();
		allData.forEach((Document data) -> {
			try {
				Document giveawayData = data.get("giveaway", Database.EMPTY_DOCUMENT);
				long timestampNow = Clock.systemUTC().instant().getEpochSecond();
				Guild guild = shardManager.getGuildById(data.getLong("_id"));
				if (guild != null) {
					List<Document> giveaways = giveawayData.getList("giveaways", Document.class, Collections.emptyList());
					for (Document giveaway : giveaways) {
						long timeLeft = giveaway.getLong("endTimestamp") - timestampNow;
						if (timeLeft <= 0) {
							bulkData.add(GiveawayEvents.removeGiveawayAndGet(guild.getIdLong(), giveaway));
						} else {
							ScheduledFuture<?> executor = GiveawayEvents.scheduledExectuor.schedule(() -> GiveawayEvents.removeGiveaway(guild.getIdLong(), giveaway), timeLeft, TimeUnit.SECONDS);
							GiveawayEvents.putExecutor(guild.getIdLong(), giveaway.getInteger("id"), executor);
						}
					}
				}
			} catch(Throwable e) {
				e.printStackTrace();
			}
		});
		
		if (!bulkData.isEmpty()) {
			Database.get().bulkWriteGuilds(bulkData, (result, exception) -> {
				if (exception != null) {
					exception.printStackTrace();
				}
			});
		}
	}
	
}