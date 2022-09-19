package com.sx4.bot.managers;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.Updates;
import com.sx4.bot.core.Sx4;
import com.sx4.bot.database.mongo.MongoDatabase;
import com.sx4.bot.utility.FutureUtility;
import com.sx4.bot.utility.MathUtility;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message.MentionType;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

public class GiveawayManager {
	
	private final Map<Long, ScheduledFuture<?>> executors;
	
	private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

	private final Sx4 bot;
	
	public GiveawayManager(Sx4 bot) {
		this.executors = new HashMap<>();
		this.bot = bot;
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
		this.putExecutor(data.getLong("messageId"), this.executor.schedule(() -> this.endGiveaway(data), seconds, TimeUnit.SECONDS));
	}
	
	public void endGiveaway(Document data) {
		this.endGiveaway(data, false);
	}
	
	public void endGiveaway(Document data, boolean forced) {
		this.endGiveawayBulk(data, forced).thenCompose(model -> {
			if (model == null) {
				return CompletableFuture.completedFuture(null);
			}
			
			return this.bot.getMongo().updateGiveaway(model);
		}).whenComplete(MongoDatabase.exceptionally());
	}
	
	public CompletableFuture<UpdateOneModel<Document>> endGiveawayBulk(Document data) {
		return this.endGiveawayBulk(data, false);
	}
	
	public CompletableFuture<UpdateOneModel<Document>> endGiveawayBulk(Document data, boolean forced) {
		long guildId = data.getLong("guildId"), messageId = data.get("messageId", 0L);
		
		Guild guild = this.bot.getShardManager().getGuildById(guildId);
		if (guild == null) {
			return CompletableFuture.completedFuture(null);
		}
		
		GuildMessageChannel channel = guild.getChannelById(GuildMessageChannel.class, data.getLong("channelId"));
		if (channel == null) {
			return CompletableFuture.completedFuture(null);
		}

		if (!guild.getSelfMember().hasPermission(channel, Permission.VIEW_CHANNEL, Permission.MESSAGE_HISTORY, Permission.MESSAGE_EMBED_LINKS)) {
			return CompletableFuture.completedFuture(null);
		}
		
		CompletableFuture<UpdateOneModel<Document>> future = new CompletableFuture<>();
		
		channel.retrieveMessageById(messageId).queue(message -> {
			MessageReaction reaction = message.getReactions().stream()
				.filter(r -> r.getEmoji().getName().equals("🎉"))
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
					
					future.complete(new UpdateOneModel<>(Filters.eq("messageId", messageId), forced ? Updates.combine(Updates.set("endAt", Clock.systemUTC().instant().getEpochSecond()), update) : update));
					channel.sendMessage("At least " + (oldWinners.isEmpty() ? "1 person needs" : oldWinners.size() == 1 ? "1 extra person needs" : oldWinners.size() + " extra people need") + " to have entered the giveaway to pick a winner " + this.bot.getConfig().getFailureEmote()).queue();
					
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

				if (guild.getSelfMember().hasPermission(channel, Permission.MESSAGE_SEND)) {
					channel.sendMessage(String.join(", ", winnerMentions) + ", Congratulations you have won the giveaway for **" + data.getString("item") + "**").setAllowedMentions(EnumSet.of(MentionType.USER)).queue();
				}
				
				EmbedBuilder embed = new EmbedBuilder();
				embed.setTitle("Giveaway");
				embed.setDescription("**" + String.join(", ", winnerTags) + "** has won **" + data.getString("item") + "**");
				embed.setTimestamp(Instant.now());
				embed.setFooter("Giveaway Ended", null);

				if (guild.getSelfMember().hasPermission(channel, Permission.MESSAGE_SEND, Permission.MESSAGE_EMBED_LINKS)) {
					message.editMessageEmbeds(embed.build()).queue();
				}
				
				update = Updates.set("winners", winnerIds);
				
				future.complete(new UpdateOneModel<>(Filters.eq("messageId", messageId), forced ? Updates.combine(Updates.set("endAt", Clock.systemUTC().instant().getEpochSecond()), update) : update));
			});
		});
		
		this.deleteExecutor(messageId);
		
		return future;
	}
	
	public void ensureGiveaways() {
		List<CompletableFuture<UpdateOneModel<Document>>> futures = new ArrayList<>();
		this.bot.getMongo().getGiveaways(Filters.not(Filters.exists("winners"))).forEach(data -> {
			long endAt = data.getLong("endAt"), timeNow = Clock.systemUTC().instant().getEpochSecond();
			if (endAt - timeNow > 0) {
				this.putGiveaway(data, endAt - timeNow);
			} else {
				futures.add(this.endGiveawayBulk(data));
			}
		});

		if (!futures.isEmpty()) {
			FutureUtility.allOf(futures, Objects::nonNull).thenCompose(bulkData -> {
				if (!bulkData.isEmpty()) {
					return this.bot.getMongo().bulkWriteGiveaways(bulkData);
				}

				return CompletableFuture.completedFuture(null);
			}).whenComplete(MongoDatabase.exceptionally());
		}
	}
	
}
