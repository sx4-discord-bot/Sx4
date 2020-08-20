package com.sx4.bot.utility;

import club.minnced.discord.webhook.send.WebhookEmbed;

import java.util.List;

public class LoggerUtility {

    public static int getWebhookEmbedLength(WebhookEmbed embed) {
        int length = 0;

        String title = embed.getTitle() != null ? embed.getTitle().getText().trim() : null;
        if (title != null) {
            length += title.length();
        }

        String description = embed.getDescription() != null ? embed.getDescription().trim() : null;
        if (description != null) {
            length += description.length();
        }

        String author = embed.getAuthor() != null ? embed.getAuthor().getName().trim() : null;
        if (author != null) {
            length += author.length();
        }

        String footer = embed.getFooter() != null ? embed.getFooter().getText().trim() : null;
        if (footer != null) {
            length += footer.length();
        }

        for (WebhookEmbed.EmbedField field : embed.getFields()) {
            length += field.getName().trim().length() + field.getValue().trim().length();
        }

        return length;
    }

    public static int getWebhookEmbedLength(List<WebhookEmbed> embeds) {
        return embeds.stream()
            .mapToInt(LoggerUtility::getWebhookEmbedLength)
            .sum();
    }

}
