package com.sx4.bot.managers;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.Updates;
import com.sx4.bot.config.Config;
import com.sx4.bot.core.Sx4;
import com.sx4.bot.database.Database;
import com.sx4.bot.utility.MathUtility;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message.MentionType;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.entities.TextChannel;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class GiveawayManager {

	private static final GiveawayManager INSTANCE = new GiveawayManager();
	
	public static GiveawayManager get() {
		return GiveawayManager.INSTANCE;
	}
	
	private final Map<Long, ScheduledFuture<?>> executors;
	
	private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
	
	public GiveawayManager() {
		this.executors = new HashMap<>();
	}
	
	public ScheduledExecutorService getExecutor() {
		return this.executor;
	}
	
	public ScheduledFuture<?> getExecutor(long messageId) {
		return this.executors.get(messageId);
	}
	
	public void deleteExecutor(long messageId) {
		ScheduledFuture<?> executor = this.executors.remove(messageId);
		if (executor != null && !executor.isDone()) {
			executor.cancel(true);
		}
	}
	
	public void putExecutor(long messageId, ScheduledFuture<?> executor) {
		ScheduledFuture<?> oldExecutor = this.executors.remove(messageId);
		if (oldExecutor != null) {
			oldExecutor.cancel(true);
		}
		
		this.executors.put(messageId, executor);
	}
	
	public void putGiveaway(Document data, long seconds) {
		this.putExecutor(data.getLong("_id"), this.executor.schedule(() -> this.endGiveaway(data), seconds, TimeUnit.SECONDS));
	}
	
	public void endGiveaway(Document data) {
		this.endGiveaway(data, false);
	}
	
	public void endGiveaway(Document data, boolean offTime) {
		this.endGiveawayAndGet(data, offTime).thenCompose(model -> {
			if (model == null) {
				return CompletableFuture.completedFuture(null);
			}
			
			return Database.get().updateGiveaway(model);
		}).whenComplete(Database.exceptionally());
	}
	
	public CompletableFuture<UpdateOneModel<Document>> endGiveawayAndGet(Document data) {
		return this.endGiveawayAndGet(data, false);
	}
	
	public CompletableFuture<UpdateOneModel<Document>> endGiveawayAndGet(Document data, boolean offTime) {
		long guildId = data.getLong("guildId"), messageId = data.get("_id", 0L);
		
		Guild guild = Sx4.get().getShardManager().getGuildById(guildId);
		if (guild == null) {
			return CompletableFuture.completedFuture(null);
		}
		
		TextChannel channel = guild.getTextChannelById(data.getLong("channelId"));
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
				Bson update;
				if (members.size() == 0) {
					update = Updates.set("winners", Collections.EMPTY_LIST);
					
					future.complete(new UpdateOneModel<>(Filters.eq("_id", messageId), offTime ? Updates.combine(Updates.set("endAt", Clock.systemUTC().instant().getEpochSecond()), update) : update));
					channel.sendMessage("At least " + (oldWinners.isEmpty() ? "1 person needs" : oldWinners.size() == 1 ? "1 extra person needs" : oldWinners.size() + " extra people need") + " to have entered the giveaway to pick a winner " + Config.get().getFailureEmote()).queue();
					
					return;
				}
				
				Set<Member> winners = MathUtility.randomSample(members, Math.min(data.getInteger("winnersAmount"), members.size()));
				
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
				
				update = Updates.set("winners", winnerIds);
				
				future.complete(new UpdateOneModel<>(Filters.eq("_id", messageId), offTime ? Updates.combine(Updates.set("endAt", Clock.systemUTC().instant().getEpochSecond()), update) : update));
			});
		});
		
		this.deleteExecutor(messageId);
		
		return future;
	}
	
	public void ensureGiveaways() {
		Database database = Database.get();
		
		List<CompletableFuture<UpdateOneModel<Document>>> futures = new ArrayList<>();
		database.getGiveaways(Filters.not(Filters.exists("winners"))).forEach(data -> {
			long endAt = data.getLong("endAt"), timeNow = Clock.systemUTC().instant().getEpochSecond();
			if (endAt - timeNow > 0) {
				this.putGiveaway(data, endAt - timeNow);
			} else {
				futures.add(this.endGiveawayAndGet(data));
			}
		});
		
		if (!futures.isEmpty()) {
			CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
				.thenApply($ -> futures.stream().map(CompletableFuture::join).filter(Objects::nonNull).collect(Collectors.toList()))
				.thenCompose(bulkData -> {
					if (!bulkData.isEmpty()) {
						return database.bulkWriteGiveaways(bulkData);
					}
					
					return CompletableFuture.completedFuture(null);
				})
				.whenComplete(Database.exceptionally());
		}
	}
	
}
