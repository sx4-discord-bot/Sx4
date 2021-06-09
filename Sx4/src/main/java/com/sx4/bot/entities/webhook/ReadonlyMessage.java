package com.sx4.bot.entities.webhook;

public class ReadonlyMessage extends club.minnced.discord.webhook.receive.ReadonlyMessage {

	private final long webhookId;
	private final String webhookToken;

	public ReadonlyMessage(club.minnced.discord.webhook.receive.ReadonlyMessage message, long webhookId, String webhookToken) {
		super(message.getId(), message.getChannelId(), message.isMentionsEveryone(), message.isTTS(), message.getAuthor(), message.getContent(), message.getEmbeds(), message.getAttachments(), message.getMentionedUsers(), message.getMentionedRoles());

		this.webhookId = webhookId;
		this.webhookToken = webhookToken;
	}

	public long getWebhookId() {
		return this.webhookId;
	}

	public String getWebhookToken() {
		return this.webhookToken;
	}

}
