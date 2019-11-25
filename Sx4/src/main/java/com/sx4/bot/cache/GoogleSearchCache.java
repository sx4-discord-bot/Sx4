package com.sx4.bot.cache;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import javax.ws.rs.ForbiddenException;

import org.json.JSONArray;
import org.json.JSONObject;

import com.sx4.bot.core.Sx4Bot;
import com.sx4.bot.interfaces.Sx4Callback;
import com.sx4.bot.utils.TokenUtils;

import okhttp3.Request;

public class GoogleSearchCache {

	public static final GoogleSearchCache INSTANCE = new GoogleSearchCache();
	
	private final Map<String, List<GoogleSearchResult>> cache = new HashMap<>();
	private final Map<String, List<GoogleSearchResult>> imageCache = new HashMap<>();
	
	private GoogleSearchCache() {}
	
	public class GoogleSearchResult {
		
		private final String title;
		private final String snippet;
		private final String link;
		
		public GoogleSearchResult(JSONObject data) {
			this.title = data.getString("title");
			this.snippet = data.getString("snippet");
			this.link = data.getString("link");
		}
		
		public String getTitle() {
			return this.title;
		}
		
		public String getSnippet() {
			return this.snippet;
		}
		
		public String getLink() {
			return this.link;
		}
		
	}
	
	public void retrieveResultsByQuery(String query, boolean includeNSFW, BiConsumer<List<GoogleSearchResult>, Throwable> data) {
		this.retrieveResultsByQuery(query, false, includeNSFW, data);
	}
	
	public void retrieveResultsByQuery(String query, boolean imageSearch, boolean includeNSFW, BiConsumer<List<GoogleSearchResult>, Throwable> data) {
		Map<String, List<GoogleSearchResult>> cache = imageSearch ? this.imageCache : this.cache;
		if (cache.containsKey(query)) {
			data.accept(cache.get(query), null);
		} else {
			Request request = new Request.Builder()
					.url("https://www.googleapis.com/customsearch/v1?key=" + TokenUtils.GOOGLE + "&cx=014023765838117903829:mm334tqd3kg" + (imageSearch ? "&searchType=image" : "") + "&safe=" + (includeNSFW ? "off" : "active") + "&q=" + query)
					.build();
			
			Sx4Bot.client.newCall(request).enqueue((Sx4Callback) response -> {
				if (response.code() == 403) {
					data.accept(null, new ForbiddenException("Daily quota reached (100)"));
					return;
				}
				
				JSONObject json = new JSONObject(response.body().string());
				JSONArray results = json.optJSONArray("items");
				
				List<GoogleSearchResult> endResults = new ArrayList<>();
				if (results != null) {
					for (int i = 0; i < results.length(); i++) {
						JSONObject result = results.getJSONObject(i);
						endResults.add(new GoogleSearchResult(result));
					}
				}
				
				cache.put(query, endResults);
				
				data.accept(endResults, null);
			});
		}
	}
	
}
