package com.sx4.bot.entities.webhook;

import club.minnced.discord.webhook.send.AllowedMentions;
import okhttp3.OkHttpClient;

import java.util.concurrent.ScheduledExecutorService;

public class WebhookClient extends club.minnced.discord.webhook.WebhookClient {

	private final String token;

	public WebhookClient(long id, String token, ScheduledExecutorService pool, OkHttpClient client) {
		super(id, token, true, client, pool, AllowedMentions.all());

		this.token = token;
	}

	public String getToken() {
		return this.token;
	}

}
