package com.sx4.bot.handlers;

import club.minnced.discord.webhook.send.WebhookMessage;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Updates;
import com.sx4.bot.core.Sx4;
import com.sx4.bot.database.mongo.MongoDatabase;
import com.sx4.bot.entities.webhook.WebhookChannel;
import com.sx4.bot.managers.WelcomerManager;
import com.sx4.bot.utility.ExceptionUtility;
import com.sx4.bot.utility.MessageUtility;
import com.sx4.bot.utility.WelcomerUtility;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.unions.GuildMessageChannelUnion;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.update.GuildMemberUpdatePendingEvent;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.requests.ErrorResponse;
import org.bson.Document;
import org.jetbrains.annotations.NotNull;

import java.time.Clock;
import java.util.List;

public class WelcomerHandler implements EventListener {

	private final Sx4 bot;

	public WelcomerHandler(Sx4 bot) {
		this.bot = bot;
	}

	public void sendWelcomer(Guild guild, Member member, boolean screeningEvent) {
		JDA jda = guild.getJDA();

		Document data = this.bot.getMongo().getGuildById(guild.getIdLong(), Projections.include("welcomer", "premium.endAt"));

		Document welcomer = data.get("welcomer", MongoDatabase.EMPTY_DOCUMENT);
		Document image = welcomer.get("image", MongoDatabase.EMPTY_DOCUMENT);

		boolean screening = welcomer.get("screening", true);
		if ((screeningEvent && !screening) || member.isPending() == screening) {
			return;
		}

		boolean messageEnabled = welcomer.getBoolean("enabled", false), imageEnabled = image.getBoolean("enabled", false);
		if (!messageEnabled && !imageEnabled) {
			return;
		}

		long channelId = welcomer.get("channelId", 0L);
		GuildMessageChannelUnion channel = channelId == 0L ? null : guild.getChannelById(GuildMessageChannelUnion.class, channelId);

		boolean dm = welcomer.getBoolean("dm", false);
		if (channel == null && !dm) {
			return;
		}

		Document webhookData = welcomer.get("webhook", MongoDatabase.EMPTY_DOCUMENT);

		boolean premium = Clock.systemUTC().instant().getEpochSecond() < data.getEmbedded(List.of("premium", "endAt"), 0L);

		WelcomerUtility.getWelcomerMessage(this.bot.getHttpClient(), messageEnabled ? welcomer.get("message", WelcomerManager.DEFAULT_MESSAGE) : null, image.getString("bannerId"), member, this.bot.getConfig().isCanary(), imageEnabled, premium, (builder, exception) -> {
			if (exception instanceof IllegalArgumentException) {
				this.bot.getMongo().updateGuildById(guild.getIdLong(), Updates.unset("welcomer.message")).whenComplete(MongoDatabase.exceptionally());
				return;
			}

			if (ExceptionUtility.sendErrorMessage(exception)) {
				return;
			}

			if (dm) {
				member.getUser().openPrivateChannel()
					.flatMap(privateChannel -> privateChannel.sendMessage(MessageUtility.fromWebhookMessage(builder.build())))
					.queue(null, ErrorResponseException.ignore(ErrorResponse.CANNOT_SEND_TO_USER));
			} else {
				WebhookMessage message = builder
					.setUsername(premium ? webhookData.get("name", "Sx4 - Welcomer") : "Sx4 - Welcomer")
					.setAvatarUrl(premium ? webhookData.get("avatar", jda.getSelfUser().getEffectiveAvatarUrl()) : jda.getSelfUser().getEffectiveAvatarUrl())
					.build();

				this.bot.getWelcomerManager().sendWelcomer(new WebhookChannel(channel), webhookData, message);
			}
		});
	}

	public void onGuildMemberJoin(GuildMemberJoinEvent event) {
		this.sendWelcomer(event.getGuild(), event.getMember(), false);
	}

	public void onGuildMemberUpdatePending(GuildMemberUpdatePendingEvent event) {
		this.sendWelcomer(event.getGuild(), event.getMember(), true);
	}

	@Override
	public void onEvent(@NotNull GenericEvent event) {
		if (event instanceof GuildMemberJoinEvent) {
			this.onGuildMemberJoin((GuildMemberJoinEvent) event);
		} else if (event instanceof GuildMemberUpdatePendingEvent) {
			this.onGuildMemberUpdatePending((GuildMemberUpdatePendingEvent) event);
		}
	}

}
