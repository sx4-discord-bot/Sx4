package com.sx4.bot.cache;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

import org.jsoup.Jsoup;

import com.sx4.bot.core.Sx4Bot;
import com.sx4.bot.interfaces.Sx4Callback;

import okhttp3.Request;

public class DictionaryCache {

	public static final DictionaryCache INSTANCE = new DictionaryCache();
	
	private final Map<String, DictionaryResult> cache = new HashMap<>();
	
	private DictionaryCache() {}
	
	public class DictionaryResult {
		
		private final String definition;
		private final String example;
		private final String audioFile;
		private final String id;
		
		public DictionaryResult(String id, String definition, String example, String audioFile) {
			this.id = id;
			this.audioFile = audioFile;
			this.definition = definition;
			this.example = example;
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
					.url("https://www.oxfordlearnersdictionaries.com/definition/english/" + query)
					.build();
			
			Sx4Bot.client.newCall(request).enqueue((Sx4Callback) response -> {
				String body = response.body().string();
				
				int definitionIndex = body.indexOf("<span class=\"def\" ");
				
				String definition = null;
				if (definitionIndex != -1) {
					definitionIndex += 43;
					
					StringBuilder builder = new StringBuilder();
					String definitionBody = body.substring(definitionIndex);
					
					char[] characters = definitionBody.toCharArray();
					for (int i = 0; i < characters.length; i++) {
						char character = characters[i];
						if (character == '<') {
							if (characters[i + 1] == 'a') {
								int endIndex = definitionBody.indexOf(" title=", i);
								
								String url = definitionBody.substring(i + 83, endIndex - 1);
								
								int hashIndex = url.indexOf('#');
								
								String word;
								if (hashIndex != -1) {
									word = url.substring(0, hashIndex) + " ";
								} else {
									word = url;
								}
								
								builder.append(String.format("[%s](https://www.oxfordlearnersdictionaries.com/definition/english/%s)", word, url));
								
								i = endIndex + 65;
							} else {
								break;
							}
						} else {
							builder.append(character);
						}
					}
					
					definition = builder.toString();
				}
				
				int audioIndex = body.indexOf("data-src-mp3=\"");
				
				String audio = null;
				if (audioIndex != -1) {
					audioIndex += 14;
					
					audio = body.substring(audioIndex, body.indexOf("\"", audioIndex));
				}
				
				int exampleIndex = body.indexOf("<span class=\"x\">");
				
				String example = null;
				if (exampleIndex != -1) {
					exampleIndex += 16;
					
					example = Jsoup.parse(body.substring(exampleIndex, body.indexOf("</span>", exampleIndex))).text();
				}
				
				DictionaryResult result;
				if (definition == null && audio == null && example == null) {
					result = null;
				} else {
					result = new DictionaryResult(query, definition, example, audio);
				}
				
				this.cache.put(query, result);
				
				data.accept(result, null);
			});
		}
	}
	
}
