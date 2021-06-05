package com.sx4.bot.handlers;

import club.minnced.discord.webhook.send.WebhookMessage;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Updates;
import com.sx4.bot.core.Sx4;
import com.sx4.bot.database.mongo.MongoDatabase;
import com.sx4.bot.managers.WelcomerManager;
import com.sx4.bot.utility.ExceptionUtility;
import com.sx4.bot.utility.MessageUtility;
import com.sx4.bot.utility.WelcomerUtility;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.requests.ErrorResponse;
import org.bson.Document;

import java.time.Clock;
import java.util.List;

public class WelcomerHandler implements EventListener {

	private final Sx4 bot;

	public WelcomerHandler(Sx4 bot) {
		this.bot = bot;
	}

	public void onGuildMemberJoin(GuildMemberJoinEvent event) {
		Document data = this.bot.getMongo().getGuildById(event.getGuild().getIdLong(), Projections.include("welcomer", "premium.endAt"));

		Document welcomer = data.get("welcomer", MongoDatabase.EMPTY_DOCUMENT);
		Document image = welcomer.get("image", MongoDatabase.EMPTY_DOCUMENT);

		boolean messageEnabled = welcomer.get("enabled", false), imageEnabled = image.get("enabled", false);
		if (!messageEnabled && !imageEnabled) {
			return;
		}

		long channelId = welcomer.get("channelId", 0L);
		TextChannel channel = channelId == 0L ? null : event.getGuild().getTextChannelById(channelId);

		boolean dm = welcomer.get("dm", false);
		if (channel == null && !dm) {
			return;
		}

		Document webhookData = welcomer.get("webhook", MongoDatabase.EMPTY_DOCUMENT);

		boolean premium = Clock.systemUTC().instant().getEpochSecond() < data.getEmbedded(List.of("premium", "endAt"), 0L);

		WelcomerUtility.getWelcomerMessage(this.bot.getHttpClient(), messageEnabled ? welcomer.get("message", WelcomerManager.DEFAULT_MESSAGE) : null, event.getMember(), imageEnabled, premium, (builder, exception) -> {
			if (exception instanceof IllegalArgumentException) {
				this.bot.getMongo().updateGuildById(event.getGuild().getIdLong(), Updates.unset("welcomer.message")).whenComplete(MongoDatabase.exceptionally(event.getJDA().getShardManager()));
				return;
			}

			if (ExceptionUtility.sendErrorMessage(event.getJDA().getShardManager(), exception)) {
				return;
			}

			if (dm) {
				event.getUser().openPrivateChannel()
					.flatMap(privateChannel -> MessageUtility.fromWebhookMessage(privateChannel, builder.build()))
					.queue(null, ErrorResponseException.ignore(ErrorResponse.CANNOT_SEND_TO_USER));
			} else {
				WebhookMessage message = builder
					.setUsername(premium ? webhookData.get("name", "Sx4 - Welcomer") : "Sx4 - Welcomer")
					.setAvatarUrl(premium ? webhookData.get("avatar", event.getJDA().getSelfUser().getEffectiveAvatarUrl()) : event.getJDA().getSelfUser().getEffectiveAvatarUrl())
					.build();

				this.bot.getWelcomerManager().sendWelcomer(channel, webhookData, message);
			}
		});
	}

	@Override
	public void onEvent(GenericEvent event) {
		if (event instanceof GuildMemberJoinEvent) {
			this.onGuildMemberJoin((GuildMemberJoinEvent) event);
		}
	}

}
