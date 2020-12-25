package com.sx4.bot.managers.impl;

import club.minnced.discord.webhook.WebhookClient;

public interface WebhookManagerImpl {

	WebhookClient getWebhook(long id);

	WebhookClient removeWebhook(long id);

	void putWebhook(long id, WebhookClient webhook);

}
