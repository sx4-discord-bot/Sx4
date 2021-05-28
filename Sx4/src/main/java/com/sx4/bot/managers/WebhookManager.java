package com.sx4.bot.managers;

import com.sx4.bot.entities.webhook.WebhookClient;

public interface WebhookManager {

	WebhookClient getWebhook(long id);

	WebhookClient removeWebhook(long id);

	void putWebhook(long id, WebhookClient webhook);

}
