package com.sx4.bot.cache;

import com.sx4.bot.core.Sx4;
import com.sx4.bot.http.HttpCallback;
import com.sx4.bot.utility.QueryMatches;
import okhttp3.Request;
import org.bson.Document;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class SteamGameCache {

	private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

	private List<Document> games;
	private ScheduledFuture<?> future;

	private final Sx4 bot;

	public SteamGameCache(Sx4 bot) {
		this.bot = bot;
		this.games = Collections.emptyList();

		this.initiateCache();
	}

	public List<Document> getGames() {
		return this.games;
	}

	public List<Document> getGames(String name) {
		QueryMatches<Document> matches = new QueryMatches<>(name, 25);
		for (Document game : this.games) {
			matches.addValue(game, game.getString("name"));
		}

		return matches.toList();
	}

	public void initiateCache() {
		this.future = this.executor.scheduleAtFixedRate(() -> {
			Request request = new Request.Builder()
				.url("https://api.steampowered.com/ISteamApps/GetAppList/v0002/?key=" + this.bot.getConfig().getSteam() + "&format=json")
				.build();

			this.bot.getHttpClient().newCall(request).enqueue((HttpCallback) response -> {
				Document json = Document.parse(response.body().string());

				this.games = json.getEmbedded(List.of("applist", "apps"), Collections.emptyList());
			});
		}, 0, 15, TimeUnit.MINUTES);
	}

	public void restartCache() {
		if (this.future != null && !this.future.isDone()) {
			this.future.cancel(true);
		}

		this.initiateCache();
	}

}
