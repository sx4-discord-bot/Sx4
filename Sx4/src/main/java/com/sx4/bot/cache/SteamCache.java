package com.sx4.bot.cache;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.json.JSONObject;

import com.sx4.bot.core.Sx4Bot;
import com.sx4.bot.interfaces.Sx4Callback;
import com.sx4.bot.modules.ImageModule;
import com.sx4.bot.utils.TokenUtils;

import okhttp3.Request;

@SuppressWarnings("unchecked")
public class SteamCache {
	
	private static List<Map<String, Object>> games;
	
	public static List<Map<String, Object>> getGames() {
		return games;
	}

	static {
		
		Sx4Bot.scheduledExectuor.scheduleAtFixedRate(() -> {
				
			Request request = new Request.Builder().url("http://api.steampowered.com/ISteamApps/GetAppList/v0002/?key=" + TokenUtils.STEAM + "&format=json").build();
				
			ImageModule.client.newCall(request).enqueue((Sx4Callback) response -> {
				Map<String, Object> json = new JSONObject(response.body().string()).toMap();
				Map<String, Object> appList = (Map<String, Object>) json.get("applist");
						
				games = (List<Map<String, Object>>) appList.get("apps");
			});
		}, 0, 1, TimeUnit.HOURS);
		
	}
	
}
