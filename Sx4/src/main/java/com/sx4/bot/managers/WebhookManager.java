package com.sx4.bot.managers;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.WebhookClient;

public interface WebhookManager {

	WebhookClient<Message> getWebhook(long id);

	WebhookClient<Message> removeWebhook(long id);

	void putWebhook(long id, WebhookClient<Message> webhook);

}
