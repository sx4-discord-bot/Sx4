package com.sx4.bot.cache;

import com.sx4.bot.core.Sx4;
import com.sx4.bot.http.HttpCallback;
import jakarta.ws.rs.ForbiddenException;
import net.dv8tion.jda.api.exceptions.HttpException;
import okhttp3.Request;
import org.bson.Document;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class GoogleSearchCache {

	private final Map<Boolean, Map<String, List<GoogleSearchResult>>> cache = new HashMap<>();
	private final Map<Boolean, Map<String, List<GoogleSearchResult>>> imageCache = new HashMap<>();

	private final Sx4 bot;

	public GoogleSearchCache(Sx4 bot) {
		this.bot = bot;
	}

	public static class GoogleSearchResult {

		private final String title;
		private final String snippet;
		private final String link;

		public GoogleSearchResult(Document data) {
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

	public CompletableFuture<List<GoogleSearchResult>> retrieveResultsByQuery(String query, boolean includeNSFW) {
		return this.retrieveResultsByQuery(query, false, includeNSFW);
	}

	public CompletableFuture<List<GoogleSearchResult>> retrieveResultsByQuery(String query, boolean imageSearch, boolean includeNSFW) {
		CompletableFuture<List<GoogleSearchResult>> future = new CompletableFuture<>();

		Map<Boolean, Map<String, List<GoogleSearchResult>>> cache = imageSearch ? this.imageCache : this.cache;
		Map<String, List<GoogleSearchResult>> actualCache = cache.get(includeNSFW);

		if (actualCache != null && actualCache.containsKey(query)) {
			future.complete(actualCache.get(query));
		} else {
			Request request = new Request.Builder()
				.url("https://www.googleapis.com/customsearch/v1?key=" + this.bot.getConfig().getGoogle() + "&cx=014023765838117903829:mm334tqd3kg" + (imageSearch ? "&searchType=image" : "") + "&safe=" + (includeNSFW ? "off" : "active") + "&q=" + query)
				.build();

			this.bot.getHttpClient().newCall(request).enqueue((HttpCallback) response -> {
				Document json = Document.parse(response.body().string());
				if (json.containsKey("error")) {
					Document error = json.get("error", Document.class);

					int code = error.getInteger("code");
					if (code == 429) {
						future.completeExceptionally(new ForbiddenException("Daily quota reached (100)"));
					} else {
						future.completeExceptionally(new HttpException(error.get("message", "Unknown error occurred with status " + code)));
					}

					return;
				}

				List<Document> items = json.getList("items", Document.class, Collections.emptyList());
				List<GoogleSearchResult> results = items.stream().map(GoogleSearchResult::new).collect(Collectors.toList());

				cache.compute(includeNSFW, (key, value) -> {
					if (value == null) {
						Map<String, List<GoogleSearchResult>> newCache = new HashMap<>();
						newCache.put(query, results);

						return newCache;
					} else {
						value.put(query, results);

						return value;
					}
				});

				future.complete(results);
			});
		}

		return future;
	}

}
