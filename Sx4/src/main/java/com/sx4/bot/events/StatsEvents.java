package com.sx4.bot.events;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.bson.BsonDocument;
import org.bson.Document;
import org.bson.conversions.Bson;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.WriteModel;
import com.sx4.bot.core.Sx4Bot;
import com.sx4.bot.database.Database;
import com.sx4.bot.settings.Settings;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberLeaveEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.sharding.ShardManager;

public class StatsEvents extends ListenerAdapter {
	
	public static final int DAY_IN_SECONDS = 86400;
	
	public static void initializeBotLogs() {
		ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
		
		Sx4Bot.scheduledExectuor.scheduleAtFixedRate(() -> {
			ShardManager shardManager = Sx4Bot.getShardManager();
			long timestampNow = Clock.systemUTC().instant().getEpochSecond();
			
			Bson filter = Filters.gte("timestamp", timestampNow - StatsEvents.DAY_IN_SECONDS);
			
			int guildsGained = Database.get().getGuildsGained(filter);
			long commandsUsed = Database.get().getCommandLogs().countDocuments(filter);
			
			EmbedBuilder embed = new EmbedBuilder();
			embed.setColor(Settings.EMBED_COLOUR);
			embed.setTimestamp(Instant.now());
			embed.setAuthor("Bot Logs", null, shardManager.getShards().get(0).getSelfUser().getEffectiveAvatarUrl());
			embed.addField("Average Command Usage", String.format("1 every %.2f seconds (%,d)", (double) StatsEvents.DAY_IN_SECONDS / commandsUsed, commandsUsed), false);
			embed.addField("Servers", String.format("%,d", shardManager.getGuilds().size()) + " (" + (guildsGained < 0 ? "" : "+") + String.format("%,d)", guildsGained), false);
			embed.addField("Users", String.format("%,d", shardManager.getUsers().size()), false);
			shardManager.getGuildById(Settings.SUPPORT_SERVER_ID).getTextChannelById(Settings.BOT_LOGS_ID).sendMessage(embed.build()).queue();
			
			Database.get().updateManyGuilds(Updates.unset("stats"), (result, exception) -> {
				if (exception != null) {
					exception.printStackTrace();
				}
			});
			
		},  Duration.between(now, ZonedDateTime.of(now.getYear(), now.getMonthValue(), now.getDayOfMonth(), 0, 0, 0, 0, ZoneOffset.UTC).plusDays(1)).toSeconds(), StatsEvents.DAY_IN_SECONDS, TimeUnit.SECONDS);
	}
	
	private static final Map<Long, Map<String, Integer>> GUILD_STATS = new HashMap<>();
	
	public static void initializeGuildStats() {
		Sx4Bot.scheduledExectuor.scheduleAtFixedRate(() -> {
			try {
				List<WriteModel<Document>> bulkData = new ArrayList<>();
				Set<Long> guildKeys = GUILD_STATS.keySet();
				for (long guildId : guildKeys) {
					Map<String, Integer> guildData = GUILD_STATS.get(guildId);
					
					boolean proceed = false;
					for (Integer value : guildData.values()) {
						if (value != 0) {
							proceed = true;
							break;
						}
					}
					
					if (!proceed) {
						continue;
					}
					
					Bson update = new BsonDocument();
					for (String key : guildData.keySet()) {
						update = Updates.combine(update, Updates.inc("stats." + key, guildData.get(key)));
					}
		
					bulkData.add(new UpdateOneModel<>(Filters.eq("_id", guildId), update, new UpdateOptions().upsert(true)));
				}
				
				if (!bulkData.isEmpty()) {
					Database.get().bulkWriteGuilds(bulkData, (result, exception) -> {
						if (exception != null) {
							exception.printStackTrace();
						}
					});
				}
				
				GUILD_STATS.clear();
			} catch(Exception e) {
				e.printStackTrace();
			}
		}, 3, 3, TimeUnit.MINUTES);
	}
	
	public void onGuildMessageReceived(GuildMessageReceivedEvent event) {
		if (!event.getAuthor().isBot()) {
			if (GUILD_STATS.containsKey(event.getGuild().getIdLong())) {
				Map<String, Integer> guildStatsData = GUILD_STATS.get(event.getGuild().getIdLong());
				guildStatsData.put("messages", guildStatsData.get("messages") + 1);
			} else {
				Map<String, Integer> guildStatsData = new HashMap<>();
				guildStatsData.put("messages", 1);
				guildStatsData.put("members", 0);
				GUILD_STATS.put(event.getGuild().getIdLong(), guildStatsData);
			}
		}
	}
	
	public void onGuildMemberJoin(GuildMemberJoinEvent event) {
		if (GUILD_STATS.containsKey(event.getGuild().getIdLong())) {
			Map<String, Integer> guildData = GUILD_STATS.get(event.getGuild().getIdLong());
			guildData.put("members", guildData.get("members") + 1);
		} else {
			Map<String, Integer> guildData = new HashMap<>();
			guildData.put("messages", 0);
			guildData.put("members", 1);
			GUILD_STATS.put(event.getGuild().getIdLong(), guildData);
		}
	}
	
	public void onGuildMemberLeave(GuildMemberLeaveEvent event) {
		if (GUILD_STATS.containsKey(event.getGuild().getIdLong())) {
			Map<String, Integer> guildData = GUILD_STATS.get(event.getGuild().getIdLong());
			guildData.put("members", guildData.get("members") - 1);
		} else {
			Map<String, Integer> guildData = new HashMap<>();
			guildData.put("messages", 0);
			guildData.put("members", -1);
			GUILD_STATS.put(event.getGuild().getIdLong(), guildData);
		}
	}
	
	public void onGuildJoin(GuildJoinEvent event) {
		Document guildLog = new Document("guildId", event.getGuild().getIdLong())
				.append("joined", true)
				.append("guildName", event.getGuild().getName())
				.append("memberCount", event.getGuild().getMembers().size())
				.append("timestamp", Clock.systemUTC().instant().getEpochSecond());
		
		Database.get().insertGuildLog(guildLog, (result, exception) -> {
			if (exception != null) {
				exception.printStackTrace();
			}
		});
	}
	
	public void onGuildLeave(GuildLeaveEvent event) {
		Document guildLog = new Document("guildId", event.getGuild().getIdLong())
				.append("joined", false)
				.append("guildName", event.getGuild().getName())
				.append("memberCount", event.getGuild().getMembers().size())
				.append("timestamp", Clock.systemUTC().instant().getEpochSecond());
		
		Database.get().insertGuildLog(guildLog, (result, exception) -> {
			if (exception != null) {
				exception.printStackTrace();
			}
		});
	}

}
