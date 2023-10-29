package com.sx4.bot.entities.webhook;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.internal.entities.ReceivedMessage;

public class SentWebhookMessage extends ReceivedMessage {

	private final long webhookId;
	private final String webhookToken;

	public SentWebhookMessage(Message message, long webhookId, String webhookToken) {
		super(message.getIdLong(), message.getChannelIdLong(), message.getGuildIdLong(), message.getJDA(), message.getGuild(),
			message.getChannel(), message.getType(), message.getMessageReference(), message.isWebhookMessage(),
			message.getApplicationIdLong(), message.isTTS(), message.isPinned(), message.getContentRaw(), message.getNonce(),
			message.getAuthor(), message.getMember(), message.getActivity(), message.getTimeEdited(), message.getMentions(),
			message.getReactions(), message.getAttachments(), message.getEmbeds(), message.getStickers(), message.getActionRows(),
			(int) message.getFlagsRaw(), message.getInteraction(), message.getStartedThread(), message.getChannelType().isThread() ? message.getApproximatePosition() : -1
		);

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
