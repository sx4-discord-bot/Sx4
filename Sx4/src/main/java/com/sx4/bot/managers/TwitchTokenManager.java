package com.sx4.bot.managers;

import com.sx4.bot.core.Sx4;
import com.sx4.bot.http.HttpCallback;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.bson.Document;

import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class TwitchTokenManager {

	private final ExecutorService executor = Executors.newSingleThreadExecutor();

	private final Sx4 bot;

	public TwitchTokenManager(Sx4 bot) {
		this.bot = bot;

		long expiresIn = this.bot.getConfig().getTwitchExpiresAt() - Clock.systemUTC().instant().getEpochSecond();
		if (expiresIn <= 0) {
			this.retrieveToken();
		} else {
			this.schedule(expiresIn);
		}
	}

	public void schedule(long seconds) {
		this.bot.getScheduledExecutor().schedule(this::retrieveToken, seconds, TimeUnit.SECONDS);
	}

	public void retrieveToken() {
		Request request = new Request.Builder()
			.post(RequestBody.create(null, new byte[0]))
			.url("https://id.twitch.tv/oauth2/token?client_id=" + this.bot.getConfig().getTwitchClientId() + "&client_secret=" + this.bot.getConfig().getTwitchClientSecret() + "&grant_type=client_credentials")
			.build();

		this.bot.getHttpClient().newCall(request).enqueue((HttpCallback) response -> {
			Document json = Document.parse(response.body().string());

			this.bot.getConfig()
				.set("token.twitch.token", json.getString("access_token"))
				.set("token.twitch.expiresAt", Clock.systemUTC().instant().getEpochSecond() + json.getInteger("expires_in"))
				.update();

			this.schedule(json.getInteger("expires_in"));
		});
	}

}
