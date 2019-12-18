package com.sx4.bot.cache;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

import javax.ws.rs.ForbiddenException;

import org.json.JSONObject;

import com.sx4.bot.core.Sx4Bot;
import com.sx4.bot.interfaces.Sx4Callback;
import com.sx4.bot.utils.TokenUtils;

import okhttp3.Request;

public class DictionaryCache {

	public static final DictionaryCache INSTANCE = new DictionaryCache();
	
	private final Map<String, DictionaryResult> cache = new HashMap<>();
	
	private DictionaryCache() {}
	
	public class DictionaryResult {
		
		private final JSONObject result;
		private final JSONObject lexicalEntry;
		private final JSONObject pronunciation;
		private final JSONObject entry;
		private final JSONObject sense;
		
		private final String definition;
		private final String example;
		private final String audioFile;
		private final String id;
		
		public DictionaryResult(JSONObject data) {
			this.result = data.getJSONArray("results").isEmpty() ? new JSONObject() : data.getJSONArray("results").getJSONObject(0);
			this.lexicalEntry = this.result.has("lexicalEntries") ? this.result.getJSONArray("lexicalEntries").getJSONObject(0) : new JSONObject();
			this.pronunciation = this.lexicalEntry.has("pronunciations") ? this.lexicalEntry.getJSONArray("pronunciations").getJSONObject(0) : new JSONObject();
			this.entry = this.lexicalEntry.has("entries") ? this.lexicalEntry.getJSONArray("entries").getJSONObject(0) : new JSONObject();
			this.sense = this.entry.has("senses") ? this.entry.getJSONArray("senses").getJSONObject(0) : new JSONObject();
			
			this.id = data.optString("id");
			this.audioFile = this.pronunciation.optString("audioFile");
			this.definition = this.sense.has("definitions") ? this.sense.getJSONArray("definitions").getString(0) : null;
			this.example = this.sense.has("examples") ? this.sense.getJSONArray("examples").getJSONObject(0).getString("text") : null;
		}
		
		public JSONObject getLexicalEntry() {
			return this.lexicalEntry;
		}
		
		public JSONObject getPronunciation() {
			return this.pronunciation;
		}
		
		public JSONObject getEntry() {
			return this.entry;
		}
		
		public JSONObject getSense() {
			return this.sense;
		}
		
		public String getId() {
			return this.id;
		}
		
		public boolean hasDefinition() {
			return this.definition != null;
		}
		
		public String getDefinition() {
			return this.definition;
		}
		
		public boolean hasExample() {
			return this.example != null;
		}
		
		public String getExample() {
			return this.example;
		}
		
		public boolean hasAudioFile() {
			return this.audioFile != null;
		}
		
		public String getAudioFile() {
			return this.audioFile;
		}
		
	}
	
	public void retrieveResultByQuery(String query, BiConsumer<DictionaryResult, Throwable> data) {
		if (this.cache.containsKey(query)) {
			data.accept(this.cache.get(query), null);
		} else {
			Request request = new Request.Builder()
					.url("https://od-api.oxforddictionaries.com:443/api/v2/entries/en-gb/" + query)
					.addHeader("Accept", "application/json")
					.addHeader("app_id", "e01b354a")
					.addHeader("app_key", TokenUtils.OXFORD_DICTIONARIES)
					.build();
			
			Sx4Bot.client.newCall(request).enqueue((Sx4Callback) response -> {
				DictionaryResult result;
				if (response.code() == 404) {
					result = null;
				} else if (response.code() == 403) {
					data.accept(null, new ForbiddenException("Monthly quota reached (1000)"));
					return;
				} else {
					JSONObject json = new JSONObject(response.body().string());
					result = new DictionaryResult(json);
				}
				
				this.cache.put(query, result);
				
				data.accept(result, null);
			});
		}
	}
	
}
