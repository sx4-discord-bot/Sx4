package com.sx4.bot.handlers;

import club.minnced.discord.webhook.send.WebhookMessage;
import club.minnced.discord.webhook.send.WebhookMessageBuilder;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Updates;
import com.sx4.bot.database.Database;
import com.sx4.bot.managers.LeaverManager;
import com.sx4.bot.utility.LeaverUtility;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.bson.Document;

public class LeaverHandler extends ListenerAdapter {

	private final Database database = Database.get();

	public void onGuildMemberRemove(GuildMemberRemoveEvent event) {
		Document data = this.database.getGuildById(event.getGuild().getIdLong(), Projections.include("welcomer", "premium.endAt"));

		Document leaver = data.get("leaver", Database.EMPTY_DOCUMENT);

		if (!leaver.get("enabled", false)) {
			return;
		}

		long channelId = leaver.get("channelId", 0L);

		TextChannel channel = channelId == 0L ? null : event.getGuild().getTextChannelById(channelId);
		if (channel == null) {
			return;
		}

		WebhookMessageBuilder builder;
		try {
			builder = LeaverUtility.getLeaverMessage(leaver.get("message", LeaverManager.DEFAULT_MESSAGE), event.getMember());
		} catch (IllegalArgumentException e) {
			this.database.updateGuildById(event.getGuild().getIdLong(), Updates.unset("welcomer.message")).whenComplete(Database.exceptionally());
			return;
		}

		Document webhookData = leaver.get("webhook", Database.EMPTY_DOCUMENT);

		WebhookMessage message = builder
			.setUsername(webhookData.get("name", "Sx4 - Leaver"))
			.setAvatarUrl(webhookData.get("avatar", event.getJDA().getSelfUser().getEffectiveAvatarUrl()))
			.build();

		LeaverManager.get().sendLeaver(channel, webhookData, message);
	}

}
