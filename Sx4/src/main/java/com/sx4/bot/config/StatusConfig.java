package com.sx4.bot.config;

public class StatusConfig extends GenericConfig {

	public StatusConfig() {
		super("../status.json");
	}

	public long getStatusWebhookId() {
		return this.get("webhook.id");
	}

	public String getStatusWebhookToken() {
		return this.get("webhook.token");
	}

	public String getStatusMessageId() {
		return this.get("messageId");
	}

}
