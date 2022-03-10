package com.sx4.bot.entities.webhook;

import club.minnced.discord.webhook.send.AllowedMentions;
import club.minnced.discord.webhook.util.ThreadPools;
import okhttp3.OkHttpClient;

import java.util.concurrent.ScheduledExecutorService;

public class WebhookClient extends club.minnced.discord.webhook.WebhookClient {

	private final String token;

	public WebhookClient(long id, String token, ScheduledExecutorService pool, OkHttpClient client) {
		super(id, token, true, client, pool == null ? ThreadPools.getDefaultPool(id, null, false) : pool, AllowedMentions.all(), 0);

		this.token = token;
	}

	public WebhookClient(long id, String token, OkHttpClient client) {
		this(id, token, null, client);
	}

	public String getToken() {
		return this.token;
	}

}
