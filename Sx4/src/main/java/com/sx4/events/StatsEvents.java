package com.sx4.events;

import static com.rethinkdb.RethinkDB.r;

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

import com.rethinkdb.gen.ast.Get;
import com.rethinkdb.gen.ast.Table;
import com.rethinkdb.model.MapObject;
import com.rethinkdb.model.OptArgs;
import com.rethinkdb.net.Connection;
import com.sx4.core.Sx4Bot;
import com.sx4.settings.Settings;

import net.dv8tion.jda.bot.sharding.ShardManager;
import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.core.events.guild.member.GuildMemberLeaveEvent;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.core.hooks.ListenerAdapter;

public class StatsEvents extends ListenerAdapter {
	
	public static void initializeBotLogs() {
		ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
		
		Sx4Bot.scheduledExectuor.scheduleAtFixedRate(() -> {
			ShardManager shardManager = Sx4Bot.getShardManager();
			Connection connection = Sx4Bot.getConnection();
			
			Get data = r.table("botstats").get("stats");
			Map<String, Object> dataRan = data.run(connection);
			int servers = (int) (shardManager.getGuilds().size() - (long) dataRan.get("servercountbefore"));
			
			EmbedBuilder embed = new EmbedBuilder();
			embed.setColor(Settings.EMBED_COLOUR);
			embed.setTimestamp(Instant.now());
			embed.setAuthor("Bot Logs", null, shardManager.getShards().get(0).getSelfUser().getEffectiveAvatarUrl());
			embed.addField("Average Command Usage", String.format("1 every %.2f seconds (%,d)", (double) 86400 / (long) dataRan.get("commands"), (long) dataRan.get("commands")), false);
			embed.addField("Servers", String.format("%,d", shardManager.getGuilds().size()) + " (" + (servers < 0 ? "" : "+") + String.format("%,d)", servers), false);
			embed.addField("Users", String.format("%,d", shardManager.getUsers().size()), false);
			shardManager.getGuildById(Settings.SUPPORT_SERVER_ID).getTextChannelById(Settings.BOT_LOGS_ID).sendMessage(embed.build()).queue();
			
			data.update(r.hashMap("commands", 0).with("servercountbefore", shardManager.getGuilds().size()).with("messages", 0)).runNoReply(connection);
			r.table("stats").forEach(table -> r.table("stats").get(table.g("id")).update(r.hashMap("messages", 0).with("members", 0))).runNoReply(connection);
		},  Duration.between(now, ZonedDateTime.of(now.getYear(), now.getMonthValue(), now.getDayOfMonth(), 0, 0, 0, 0, ZoneOffset.UTC).plusDays(1)).toSeconds(), 86400, TimeUnit.SECONDS);
	}
	
	{	
		Sx4Bot.scheduledExectuor.scheduleAtFixedRate(() -> {
			Table table = r.table("stats");
			Connection connection = Sx4Bot.getConnection();
			
			Set<String> guildKeys = this.guildStats.keySet();
			
			List<MapObject> massData = new ArrayList<>();
			for (String guildId : guildKeys) {
				massData.add(r.hashMap("id", guildId).with("members", 0).with("messages", 0));
			}
			
			table.insert(massData).run(connection, OptArgs.of("durability", "soft"));
			
			for (String guildId : guildKeys) {
				Map<String, Integer> guildData = this.guildStats.get(guildId);
				
				boolean proceed = false;
				for (Integer value : guildData.values()) {
					if (value != 0) {
						proceed = true;
						break;
					}
				}
				
				if (proceed == false) {
					break;
				}
				
				table.get(guildId).update(row -> r.hashMap("messages", row.g("messages").add(guildData.get("messages"))).with("members", row.g("members").add(guildData.get("members")))).runNoReply(connection);
				this.guildStats.clear();
			}
		}, 3, 3, TimeUnit.MINUTES);
	}
	
	private Map<String, Map<String, Integer>> guildStats = new HashMap<>();
	
	public void onGuildMessageReceived(GuildMessageReceivedEvent event) {
		if (event.getAuthor().equals(event.getJDA().getSelfUser())) {
			r.table("botstats").get("stats").update(row -> r.hashMap("messages", row.g("messages").add(1))).runNoReply(Sx4Bot.getConnection());
			return;
		}
		
		if (event.getAuthor().isBot()) {
			return;
		}
		
		if (this.guildStats.containsKey(event.getGuild().getId())) {
			Map<String, Integer> guildData = this.guildStats.get(event.getGuild().getId());
			guildData.put("messages", guildData.get("messages") + 1);
			this.guildStats.put(event.getGuild().getId(), guildData);
		} else {
			Map<String, Integer> guildData = new HashMap<>();
			guildData.put("messages", 1);
			guildData.put("members", 0);
			this.guildStats.put(event.getGuild().getId(), guildData);
		}
	}
	
	public void onGuildMemberJoin(GuildMemberJoinEvent event) {
		if (this.guildStats.containsKey(event.getGuild().getId())) {
			Map<String, Integer> guildData = this.guildStats.get(event.getGuild().getId());
			guildData.put("members", guildData.get("members") + 1);
			this.guildStats.put(event.getGuild().getId(), guildData);
		} else {
			Map<String, Integer> guildData = new HashMap<>();
			guildData.put("messages", 0);
			guildData.put("members", 1);
			this.guildStats.put(event.getGuild().getId(), guildData);
		}
	}
	
	public void onGuildMemberLeave(GuildMemberLeaveEvent event) {
		if (this.guildStats.containsKey(event.getGuild().getId())) {
			Map<String, Integer> guildData = this.guildStats.get(event.getGuild().getId());
			guildData.put("members", guildData.get("members") - 1);
			this.guildStats.put(event.getGuild().getId(), guildData);
		} else {
			Map<String, Integer> guildData = new HashMap<>();
			guildData.put("messages", 0);
			guildData.put("members", -1);
			this.guildStats.put(event.getGuild().getId(), guildData);
		}
	}

}
