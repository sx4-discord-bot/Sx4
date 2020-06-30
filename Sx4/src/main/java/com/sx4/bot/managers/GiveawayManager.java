package com.sx4.bot.managers;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.bson.Document;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.Updates;
import com.sx4.bot.config.Config;
import com.sx4.bot.core.Sx4Bot;
import com.sx4.bot.database.Database;
import com.sx4.bot.utility.ExceptionUtility;
import com.sx4.bot.utility.MathUtility;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message.MentionType;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.entities.TextChannel;

public class GiveawayManager {

	private static final GiveawayManager INSTANCE = new GiveawayManager();
	
	public static GiveawayManager get() {
		return GiveawayManager.INSTANCE;
	}
	
	private final Map<Long, Map<Long, ScheduledFuture<?>>> executors;
	
	private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
	
	public GiveawayManager() {
		this.executors = new HashMap<>();
	}
	
	public ScheduledExecutorService getExecutor() {
		return this.executor;
	}
	
	public ScheduledFuture<?> getExecutor(long guildId, long messageId) {
		Map<Long, ScheduledFuture<?>> giveaways = this.executors.get(guildId);
		if (giveaways != null) {
			return giveaways.get(messageId);
		}
		
		return null;
	}
	
	public void cancelExecutor(long guildId, long messageId) {
		Map<Long, ScheduledFuture<?>> giveaways = this.executors.get(guildId);
		if (giveaways != null) {
			ScheduledFuture<?> executor = giveaways.remove(messageId);
			if (executor != null) {
				executor.cancel(true);
			}
		}
	}
	
	public void putExecutor(long guildId, long messageId, ScheduledFuture<?> executor) {
		Map<Long, ScheduledFuture<?>> giveaways = this.executors.get(guildId);
		if (giveaways != null) {
			ScheduledFuture<?> oldExecutor = giveaways.remove(messageId);
			if (oldExecutor != null) {
				oldExecutor.cancel(true);
			}
			
			giveaways.put(messageId, executor);
		}
		
		giveaways = new HashMap<>();
		giveaways.put(messageId, executor);
		
		this.executors.put(guildId, giveaways);
	}
	
	public void putGiveaway(Document data, long seconds) {
		this.putExecutor(data.get("guildId", 0L), data.get("_id", 0L), this.executor.schedule(() -> this.endGiveaway(data), seconds, TimeUnit.SECONDS));
	}
	
	public void endGiveaway(Document data) {
		this.endGiveawayAndGet(data).thenCompose(model -> {
			if (model == null) {
				return CompletableFuture.completedStage(null);
			}
			
			return Database.get().updateGiveaway(model);
		}).whenComplete((result, excpetion) -> ExceptionUtility.sendErrorMessage(excpetion));
	}
	
	public CompletableFuture<UpdateOneModel<Document>> endGiveawayAndGet(Document data) {
		long guildId = data.get("guildId", 0L), messageId = data.get("_id", 0L);
		
		Guild guild = Sx4Bot.getShardManager().getGuildById(guildId);
		if (guild == null) {
			return CompletableFuture.completedFuture(null);
		}
		
		TextChannel channel = guild.getTextChannelById(data.get("channelId", 0L));
		if (channel == null) {
			return CompletableFuture.completedFuture(null);
		}
		
		CompletableFuture<UpdateOneModel<Document>> future = new CompletableFuture<>();
		
		channel.retrieveMessageById(messageId).queue(message -> {
			MessageReaction reaction = message.getReactions().stream()
				.filter(r -> r.getReactionEmote().getName().equals("🎉"))
				.findFirst()
				.orElse(null);
			
			if (reaction == null) {
				future.complete(null);
				return;
			}
			
			List<Long> oldWinners = data.getList("winners", Long.class, Collections.emptyList());
			
			List<Member> members = new ArrayList<>();
			reaction.retrieveUsers().forEachAsync(user -> {
				Member member = guild.getMember(user);
				if (member != null && member.getIdLong() != guild.getSelfMember().getIdLong() && !oldWinners.contains(member.getIdLong())) {
					members.add(member);
				}
				
				return true;
			}).thenRun(() -> {
				if (members.size() == 0) {
					future.complete(new UpdateOneModel<>(Filters.eq("_id", messageId), Updates.set("winners", Collections.EMPTY_LIST)));
					channel.sendMessage("At least " + (oldWinners.isEmpty() ? "1 person needs" : oldWinners.size() == 1 ? "1 extra person needs" : oldWinners.size() + " extra people need") + " to have entered the giveaway to pick a winner " + Config.get().getFailureEmote()).queue();
					
					return;
				}
				
				Set<Member> winners = MathUtility.randomSample(members, Math.min(data.get("winnersAmount", 0), members.size()));
				
				List<Long> winnerIds = new ArrayList<>();
				List<String> winnerTags = new ArrayList<>(), winnerMentions = new ArrayList<>();
				for (Member winner : winners) {
					winnerIds.add(winner.getIdLong());
					winnerTags.add(winner.getUser().getAsTag());
					winnerMentions.add(winner.getAsMention());
				}
				
				channel.sendMessage(String.join(", ", winnerMentions) + ", Congratulations you have won the giveaway for **" + data.getString("item") + "**").allowedMentions(EnumSet.of(MentionType.USER)).queue();
				
				EmbedBuilder embed = new EmbedBuilder();
				embed.setTitle("Giveaway");
				embed.setDescription("**" + String.join(", ", winnerTags) + "** has won **" + data.getString("item") + "**");
				embed.setTimestamp(Instant.now());
				embed.setFooter("Giveaway Ended", null);
				
				message.editMessage(embed.build()).queue();
				
				future.complete(new UpdateOneModel<>(Filters.eq("_id", messageId), Updates.set("winners", winnerIds)));
			});
		});
		
		this.cancelExecutor(guildId, messageId);
		
		return future;
	}
	
	public void ensureGiveaways() {
		Database database = Database.get();
		
		List<CompletableFuture<UpdateOneModel<Document>>> futures = new ArrayList<>();
		database.getGiveaways(Filters.not(Filters.exists("winners"))).forEach(data -> {
			long endAt = data.get("endAt", 0L), timeNow = Clock.systemUTC().instant().getEpochSecond();
			if (endAt - timeNow > 0) {
				this.putGiveaway(data, endAt - timeNow);
			} else {
				futures.add(this.endGiveawayAndGet(data));
			}
		});
		
		CompletableFuture.allOf(futures.toArray(new CompletableFuture[futures.size()]))
			.thenApply($ -> futures.stream().map(CompletableFuture::join).filter(Objects::nonNull).collect(Collectors.toList()))
			.thenCompose(bulkData -> database.bulkWriteGiveaways(bulkData))
			.whenComplete((result, exception) -> ExceptionUtility.sendErrorMessage(exception));
	}
	
}
