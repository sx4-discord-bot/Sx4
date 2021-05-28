package com.sx4.bot.handlers;

import club.minnced.discord.webhook.send.WebhookMessage;
import club.minnced.discord.webhook.send.WebhookMessageBuilder;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Updates;
import com.sx4.bot.core.Sx4;
import com.sx4.bot.database.mongo.MongoDatabase;
import com.sx4.bot.managers.LeaverManager;
import com.sx4.bot.utility.LeaverUtility;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import org.bson.Document;

public class LeaverHandler implements EventListener {

	private final Sx4 bot;

	public LeaverHandler(Sx4 bot) {
		this.bot = bot;
	}

	public void onGuildMemberRemove(GuildMemberRemoveEvent event) {
		Document data = this.bot.getMongo().getGuildById(event.getGuild().getIdLong(), Projections.include("welcomer", "premium.endAt"));

		Document leaver = data.get("leaver", MongoDatabase.EMPTY_DOCUMENT);

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
			this.bot.getMongo().updateGuildById(event.getGuild().getIdLong(), Updates.unset("welcomer.message")).whenComplete(MongoDatabase.exceptionally(this.bot.getShardManager()));
			return;
		}

		Document webhookData = leaver.get("webhook", MongoDatabase.EMPTY_DOCUMENT);

		WebhookMessage message = builder
			.setUsername(webhookData.get("name", "Sx4 - Leaver"))
			.setAvatarUrl(webhookData.get("avatar", event.getJDA().getSelfUser().getEffectiveAvatarUrl()))
			.build();

		this.bot.getLeaverManager().sendLeaver(channel, webhookData, message);
	}

	@Override
	public void onEvent(GenericEvent event) {
		if (event instanceof GuildMemberRemoveEvent) {
			this.onGuildMemberRemove((GuildMemberRemoveEvent) event);
		}
	}

}
