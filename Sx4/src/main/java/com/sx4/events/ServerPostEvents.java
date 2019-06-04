package com.sx4.events;

import java.util.concurrent.TimeUnit;

import org.json.JSONObject;

import com.sx4.core.Sx4Bot;
import com.sx4.interfaces.Sx4Callback;
import com.sx4.settings.Settings;
import com.sx4.utils.TokenUtils;

import net.dv8tion.jda.bot.sharding.ShardManager;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;

public class ServerPostEvents {

	public static void initializePosting() {
		if (!Settings.CANARY) {
			Sx4Bot.scheduledExectuor.scheduleAtFixedRate(() -> {
				ShardManager shardManager = Sx4Bot.getShardManager();
				String botId = shardManager.getApplicationInfo().getJDA().getSelfUser().getId();
				int guildCount = shardManager.getGuilds().size();
				int shardCount = shardManager.getShardsTotal();
				
				String bodyDiscordBots = new JSONObject()
						.put("guildCount", guildCount)
						.put("shardCount", shardCount)
						.toString();
				String bodyDiscordBotList = new JSONObject()
						.put("server_count", guildCount)
						.put("shard_count", shardCount)
						.toString();
				String bodyBotListSpace = new JSONObject()
						.put("server_count", guildCount)
						.put("shards", shardCount)
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
						.post(RequestBody.create(MediaType.parse("application/json; charset=utf-8"), bodyDiscordBotList))
						.url("https://discordbots.org/api/bots/" + botId + "/stats")
						.addHeader("Authorization", TokenUtils.DISCORD_BOT_LIST)
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
			}, 0, 5, TimeUnit.MINUTES);
		}
	}
	
}
