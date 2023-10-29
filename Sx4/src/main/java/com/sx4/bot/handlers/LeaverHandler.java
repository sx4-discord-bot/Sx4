package com.sx4.bot.handlers;

import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Updates;
import com.sx4.bot.core.Sx4;
import com.sx4.bot.database.mongo.MongoDatabase;
import com.sx4.bot.entities.webhook.WebhookChannel;
import com.sx4.bot.managers.LeaverManager;
import com.sx4.bot.utility.LeaverUtility;
import net.dv8tion.jda.api.entities.channel.unions.GuildMessageChannelUnion;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import org.bson.Document;
import org.jetbrains.annotations.NotNull;

import java.time.Clock;
import java.util.List;

public class LeaverHandler implements EventListener {

	private final Sx4 bot;

	public LeaverHandler(Sx4 bot) {
		this.bot = bot;
	}

	public void onGuildMemberRemove(GuildMemberRemoveEvent event) {
		Document data = this.bot.getMongo().getGuildById(event.getGuild().getIdLong(), Projections.include("leaver", "premium.endAt"));

		Document leaver = data.get("leaver", MongoDatabase.EMPTY_DOCUMENT);

		if (!leaver.get("enabled", false)) {
			return;
		}

		long channelId = leaver.get("channelId", 0L);

		GuildMessageChannelUnion channel = channelId == 0L ? null : event.getGuild().getChannelById(GuildMessageChannelUnion.class, channelId);
		if (channel == null) {
			return;
		}

		MessageCreateBuilder builder;
		try {
			builder = LeaverUtility.getLeaverMessage(leaver.get("message", LeaverManager.DEFAULT_MESSAGE), event.getMember());
		} catch (IllegalArgumentException e) {
			this.bot.getMongo().updateGuildById(event.getGuild().getIdLong(), Updates.unset("leaver.message")).whenComplete(MongoDatabase.exceptionally());
			return;
		}

		boolean premium = data.getEmbedded(List.of("premium", "endAt"), 0L) >= Clock.systemUTC().instant().getEpochSecond();

		Document webhookData = leaver.get("webhook", MongoDatabase.EMPTY_DOCUMENT);

		this.bot.getLeaverManager().sendLeaver(new WebhookChannel(channel), webhookData, builder.build(), premium);
	}

	@Override
	public void onEvent(@NotNull GenericEvent event) {
		if (event instanceof GuildMemberRemoveEvent) {
			this.onGuildMemberRemove((GuildMemberRemoveEvent) event);
		}
	}

}
