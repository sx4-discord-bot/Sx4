package com.sx4.bot.events;

import java.util.concurrent.TimeUnit;

import org.json.JSONObject;

import com.sx4.bot.core.Sx4Bot;
import com.sx4.bot.interfaces.Sx4Callback;
import com.sx4.bot.settings.Settings;
import com.sx4.bot.utils.TokenUtils;

import net.dv8tion.jda.api.sharding.ShardManager;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;

public class ServerPostEvents {

	public static void initializePosting() {
		if (!Settings.CANARY) {
			Sx4Bot.scheduledExectuor.scheduleAtFixedRate(() -> {
				ShardManager shardManager = Sx4Bot.getShardManager();
				String botId = shardManager.getShardById(0).getSelfUser().getId();
				long guildCount = shardManager.getGuildCache().size();
				long userCount = shardManager.getUserCache().size();
				int shardCount = shardManager.getShardsTotal();
				
				String bodyDiscordBots = new JSONObject()
						.put("guildCount", guildCount)
						.put("shardCount", shardCount)
						.toString();
				String bodyDiscordBotListOrg = new JSONObject()
						.put("server_count", guildCount)
						.put("shard_count", shardCount)
						.toString();
				String bodyBotListSpace = new JSONObject()
						.put("server_count", guildCount)
						.put("shards", shardCount)
						.toString();
				String bodyDiscordBotListCom = new JSONObject()
						.put("guilds", guildCount)
						.put("users", userCount)
						.toString();
				
				Request request;			
				request = new Request.Builder()
						.post(RequestBody.create(MediaType.parse("application/json; charset=utf-8"), bodyDiscordBots))
						.url("https://discord.bots.gg/api/v1/bots/" + botId  + "/stats")
						.addHeader("Authorization", TokenUtils.DISCORD_BOTS)
						.addHeader("Content-Type", "application/json")
						.build();
				
				Sx4Bot.client.newCall(request).enqueue((Sx4Callback) response -> response.close());
				
				request = new Request.Builder()
						.post(RequestBody.create(MediaType.parse("application/json; charset=utf-8"), bodyDiscordBotListOrg))
						.url("https://discordbots.org/api/bots/" + botId + "/stats")
						.addHeader("Authorization", TokenUtils.DISCORD_BOT_LIST_ORG)
						.addHeader("Content-Type", "application/json")
						.build();
				
				Sx4Bot.client.newCall(request).enqueue((Sx4Callback) response -> response.close());
				
				request = new Request.Builder()
						.post(RequestBody.create(MediaType.parse("application/json; charset=utf-8"), bodyBotListSpace))
						.url("https://api.botlist.space/v1/bots/" + botId + "/")
						.addHeader("Authorization", TokenUtils.BOT_LIST_SPACE)
						.addHeader("Content-Type", "application/json")
						.build();
				
				Sx4Bot.client.newCall(request).enqueue((Sx4Callback) response -> response.close());
				
				request = new Request.Builder()
						.post(RequestBody.create(MediaType.parse("application/json; charset=utf-8"), bodyDiscordBotListCom))
						.url("https://discordbotlist.com/api/bots/" + botId + "/stats")
						.addHeader("Authorization", TokenUtils.DISCORD_BOT_LIST_COM)
						.addHeader("Content-Type", "application/json")
						.build();
				
				Sx4Bot.client.newCall(request).enqueue((Sx4Callback) response -> response.close());
			}, 0, 5, TimeUnit.MINUTES);
		}
	}
	
}
