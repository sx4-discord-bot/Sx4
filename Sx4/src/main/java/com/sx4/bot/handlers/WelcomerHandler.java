package com.sx4.bot.handlers;

import club.minnced.discord.webhook.send.WebhookMessage;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Updates;
import com.sx4.bot.core.Sx4;
import com.sx4.bot.database.Database;
import com.sx4.bot.managers.WelcomerManager;
import com.sx4.bot.utility.ExceptionUtility;
import com.sx4.bot.utility.MessageUtility;
import com.sx4.bot.utility.WelcomerUtility;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.ErrorResponse;
import org.bson.Document;

import java.time.Clock;
import java.util.List;

public class WelcomerHandler extends ListenerAdapter {

	private final Sx4 bot;

	public WelcomerHandler(Sx4 bot) {
		this.bot = bot;
	}

	public void onGuildMemberJoin(GuildMemberJoinEvent event) {
		Document data = this.bot.getDatabase().getGuildById(event.getGuild().getIdLong(), Projections.include("welcomer", "premium.endAt"));

		Document welcomer = data.get("welcomer", Database.EMPTY_DOCUMENT);
		Document image = welcomer.get("image", Database.EMPTY_DOCUMENT);

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

		Document webhookData = welcomer.get("webhook", Database.EMPTY_DOCUMENT);

		boolean gif = data.getEmbedded(List.of("premium", "endAt"), 0L) >= Clock.systemUTC().instant().getEpochSecond();

		WelcomerUtility.getWelcomerMessage(this.bot.getHttpClient(), messageEnabled ? welcomer.get("message", WelcomerManager.DEFAULT_MESSAGE) : null, event.getMember(), imageEnabled, gif, (builder, exception) -> {
			if (exception instanceof IllegalArgumentException) {
				this.bot.getDatabase().updateGuildById(event.getGuild().getIdLong(), Updates.unset("welcomer.message")).whenComplete(Database.exceptionally(event.getJDA().getShardManager()));
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
					.setUsername(webhookData.get("name", "Sx4 - Welcomer"))
					.setAvatarUrl(webhookData.get("avatar", event.getJDA().getSelfUser().getEffectiveAvatarUrl()))
					.build();

				this.bot.getWelcomerManager().sendWelcomer(channel, webhookData, message);
			}
		});
	}

}
