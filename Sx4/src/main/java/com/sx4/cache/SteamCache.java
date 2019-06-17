package com.sx4.cache;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.json.JSONException;
import org.json.JSONObject;

import com.sx4.core.Sx4Bot;
import com.sx4.interfaces.Sx4Callback;
import com.sx4.modules.ImageModule;
import com.sx4.utils.TokenUtils;

import okhttp3.Request;

public class SteamCache {
	
	private static Map<String, Object> games = new HashMap<>();
	
	public static Map<String, Object> getGames() {
		return games;
	}

	static {
		
		Sx4Bot.scheduledExectuor.scheduleAtFixedRate(() -> {
				
			Request request = new Request.Builder().url("http://api.steampowered.com/ISteamApps/GetAppList/v0002/?key=" + TokenUtils.STEAM + "&format=json").build();
				
			ImageModule.client.newCall(request).enqueue((Sx4Callback) response -> {
				JSONObject json = null;
				try {
					json = new JSONObject(response.body().string());
				} catch (JSONException | IOException e) {}
						
				games = json.toMap(); 
			});
		}, 0, 1, TimeUnit.HOURS);
		
	}
	
}
